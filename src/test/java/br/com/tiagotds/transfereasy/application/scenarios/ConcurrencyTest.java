package br.com.tiagotds.transfereasy.application.scenarios;

import static br.com.tiagotds.transfereasy.support.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;
import br.com.tiagotds.transfereasy.support.Race;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Hammers the core from many threads, once per {@link LockingStrategy}. Whatever the strategy, the same invariants
 * must hold:
 * <ul>
 *   <li>a balance never goes negative, and money is never created or destroyed;</li>
 *   <li>no update is lost, and the ledger always agrees with the balances;</li>
 *   <li>opposite concurrent transfers do not deadlock.</li>
 * </ul>
 * Under OPTIMISTIC, conflicts are expected and absorbed by the retry middleware, so the observable outcome is
 * the same as under PESSIMISTIC.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ConcurrencyTest {

    private static final int THREADS = 32;
    private static final int REPETITIONS = 3;

    /** Every strategy, several times: races are non-deterministic, so one green run proves little. */
    static Stream<LockingStrategy> strategies() {
        return Stream.of(LockingStrategy.values())
                .flatMap(strategy -> IntStream.range(0, REPETITIONS).mapToObj(i -> strategy));
    }

    private TestEnvironment env;

    private void start(LockingStrategy strategy) {
        env = new TestEnvironment(Config.defaults()
                .with("db.pool-size", String.valueOf(THREADS))
                .with("account.locking", strategy.name()));
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private void assertLedgerConsistent() {
        assertEquals(0, env.totalOfAllBalances().compareTo(env.totalOfAllLedgerEntries()),
                "sum of balances must equal sum of ledger entries");
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void concurrent_withdrawals_can_never_overdraw_the_account(LockingStrategy strategy) throws Exception {
        start(strategy);
        var account = env.accountWithBalance("111", "100");
        var succeeded = new AtomicInteger();
        var refused = new AtomicInteger();

        Race.run(THREADS, i -> () -> {
            try {
                env.withdraw(account, of("30"));
                succeeded.incrementAndGet();
            } catch (InsufficientFunds e) {
                refused.incrementAndGet();
            }
            return null;
        });

        assertEquals(3, succeeded.get(), "100 covers exactly three withdrawals of 30");
        assertEquals(THREADS - 3, refused.get());
        assertEquals(of("10"), env.balanceOf(account));
        assertLedgerConsistent();
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void concurrent_transfers_out_of_one_account_can_never_overdraw_it(LockingStrategy strategy) throws Exception {
        start(strategy);
        var source = env.accountWithBalance("1", "100");
        var target = env.accountWithBalance("2", "0");
        var succeeded = new AtomicInteger();

        Race.run(THREADS, i -> () -> {
            try {
                env.transfer(source, target, of("40"));
                succeeded.incrementAndGet();
            } catch (InsufficientFunds expected) {
                // refused: the balance no longer covers it
            }
            return null;
        });

        assertEquals(2, succeeded.get());
        assertEquals(of("20"), env.balanceOf(source));
        assertEquals(of("80"), env.balanceOf(target));
        assertLedgerConsistent();
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void concurrent_deposits_never_lose_an_update(LockingStrategy strategy) throws Exception {
        start(strategy);
        var account = env.accountWithBalance("111", "0");

        Race.run(THREADS, i -> () -> {
            for (int n = 0; n < 10; n++) {
                env.deposit(account, of("1.25"));
            }
            return null;
        });

        assertEquals(of("400"), env.balanceOf(account));
        assertEquals(THREADS * 10, env.statement(account, 1000).entries().size(),
                "every deposit must have exactly one ledger entry");
        assertLedgerConsistent();
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void opposite_transfers_between_two_accounts_do_not_deadlock_and_conserve_money(LockingStrategy strategy)
            throws Exception {
        start(strategy);
        var a = env.accountWithBalance("1", "1000");
        var b = env.accountWithBalance("2", "1000");

        Race.run(THREADS, i -> () -> {
            for (int n = 0; n < 20; n++) {
                if (i % 2 == 0) {
                    env.transfer(a, b, of("1"));
                } else {
                    env.transfer(b, a, of("1"));
                }
            }
            return null;
        });

        assertEquals(of("1000"), env.balanceOf(a), "equal traffic in both directions nets out");
        assertEquals(of("1000"), env.balanceOf(b));
        assertLedgerConsistent();
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void a_random_storm_of_transfers_among_many_accounts_conserves_money_and_never_goes_negative(
            LockingStrategy strategy) throws Exception {
        start(strategy);
        int accounts = 8;
        var numbers = new ArrayList<String>();
        for (int i = 0; i < accounts; i++) {
            numbers.add(env.accountWithBalance("c" + i, "50"));
        }

        Race.run(THREADS, seed -> () -> {
            var random = new Random(seed);
            for (int n = 0; n < 40; n++) {
                int from = random.nextInt(accounts);
                int to = (from + 1 + random.nextInt(accounts - 1)) % accounts;
                try {
                    env.transfer(numbers.get(from), numbers.get(to), BigDecimal.valueOf(1 + random.nextInt(60)));
                } catch (InsufficientFunds expected) {
                    // refused transfers change nothing
                }
            }
            return null;
        });

        for (var number : numbers) {
            assertTrue(env.balanceOf(number).signum() >= 0, "negative balance on " + number);
        }
        assertEquals(0, of("400").compareTo(env.totalOfAllBalances()), "money must be conserved");
        assertLedgerConsistent();
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void mixed_deposits_and_withdrawals_keep_the_ledger_and_balance_in_agreement(LockingStrategy strategy)
            throws Exception {
        start(strategy);
        var account = env.accountWithBalance("111", "500");

        Race.run(THREADS, i -> () -> {
            for (int n = 0; n < 25; n++) {
                try {
                    if (i % 2 == 0) {
                        env.deposit(account, of("3"));
                    } else {
                        env.withdraw(account, of("2"));
                    }
                } catch (InsufficientFunds expected) {
                    // refused withdrawals change nothing
                }
            }
            return null;
        });

        assertTrue(env.balanceOf(account).signum() >= 0);
        assertLedgerConsistent();
        var last = env.statement(account, 1).entries().getFirst();
        assertEquals(0, last.balanceAfter().value().compareTo(env.balanceOf(account)),
                "the newest ledger entry must carry the final balance");
    }

    @ParameterizedTest(name = "{0} #{index}")
    @MethodSource("strategies")
    void concurrent_creation_of_the_same_customer_yields_exactly_one_winner(LockingStrategy strategy)
            throws Exception {
        start(strategy);
        var created = new AtomicInteger();
        var conflicts = new AtomicInteger();

        Race.run(THREADS, i -> () -> {
            try {
                env.createCustomer("same", "Same Person");
                created.incrementAndGet();
            } catch (Conflict e) {
                assertInstanceOf(Conflict.class, e);
                conflicts.incrementAndGet();
            }
            return null;
        });

        assertEquals(1, created.get());
        assertEquals(THREADS - 1, conflicts.get());
        assertEquals(1, env.search(null).size());
    }
}
