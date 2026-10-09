package br.com.tiagotds.transfereasy.service;

import static br.com.tiagotds.transfereasy.support.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.DomainException;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Hammers the services from many threads. Each test releases all workers at the same instant through a latch so
 * they truly race, and then asserts the invariants that the original solution could not guarantee:
 * <ul>
 *   <li>a balance never goes negative, and money is never created or destroyed;</li>
 *   <li>the ledger always agrees with the balances;</li>
 *   <li>opposite concurrent transfers do not deadlock.</li>
 * </ul>
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConcurrencyTest {

    private static final int THREADS = 32;

    private TestEnvironment env;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        env = new TestEnvironment(THREADS);
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        env.close();
    }

    /** Runs all tasks simultaneously and waits for them; any unexpected exception fails the test. */
    private <T> List<T> race(List<Callable<T>> tasks) throws Exception {
        var ready = new CountDownLatch(tasks.size());
        var go = new CountDownLatch(1);
        var futures = new ArrayList<Future<T>>();
        for (var task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertTrue(ready.await(20, TimeUnit.SECONDS), "workers did not start");
        go.countDown();
        var results = new ArrayList<T>();
        for (var future : futures) {
            results.add(future.get(40, TimeUnit.SECONDS));
        }
        return results;
    }

    private void assertLedgerConsistent() {
        assertEquals(0, env.totalOfAllBalances().compareTo(env.totalOfAllLedgerEntries()),
                "sum of balances must equal sum of ledger entries");
    }

    @RepeatedTest(5)
    void concurrent_withdrawals_can_never_overdraw_the_account() throws Exception {
        var account = env.accountWithBalance("111", "100");
        var succeeded = new AtomicInteger();
        var refused = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    env.accounts.withdraw(account, of("30"));
                    succeeded.incrementAndGet();
                } catch (DomainException e) {
                    assertInstanceOf(InsufficientFunds.class, e);
                    refused.incrementAndGet();
                }
                return null;
            });
        }

        race(tasks);

        assertEquals(3, succeeded.get(), "100 covers exactly three withdrawals of 30");
        assertEquals(THREADS - 3, refused.get());
        assertEquals(of("10"), env.balanceOf(account));
        assertLedgerConsistent();
    }

    @RepeatedTest(5)
    void concurrent_transfers_out_of_one_account_can_never_overdraw_it() throws Exception {
        var source = env.accountWithBalance("1", "100");
        var target = env.accountWithBalance("2", "0");
        var succeeded = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    env.accounts.transfer(source, target, of("40"));
                    succeeded.incrementAndGet();
                } catch (DomainException e) {
                    assertInstanceOf(InsufficientFunds.class, e);
                }
                return null;
            });
        }

        race(tasks);

        assertEquals(2, succeeded.get());
        assertEquals(of("20"), env.balanceOf(source));
        assertEquals(of("80"), env.balanceOf(target));
        assertLedgerConsistent();
    }

    @RepeatedTest(5)
    void concurrent_deposits_never_lose_an_update() throws Exception {
        var account = env.accountWithBalance("111", "0");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                for (int n = 0; n < 10; n++) {
                    env.accounts.deposit(account, of("1.25"));
                }
                return null;
            });
        }

        race(tasks);

        assertEquals(of("400"), env.balanceOf(account));
        assertEquals(THREADS * 10, env.accounts.statement(account, 1000).entries().size(),
                "every deposit must have exactly one ledger entry (320 entries, under the 1000 cap)");
        assertLedgerConsistent();
    }

    @RepeatedTest(5)
    void opposite_transfers_between_two_accounts_do_not_deadlock_and_conserve_money() throws Exception {
        var a = env.accountWithBalance("1", "1000");
        var b = env.accountWithBalance("2", "1000");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            var forward = i % 2 == 0;
            tasks.add(() -> {
                for (int n = 0; n < 20; n++) {
                    if (forward) {
                        env.accounts.transfer(a, b, of("1"));
                    } else {
                        env.accounts.transfer(b, a, of("1"));
                    }
                }
                return null;
            });
        }

        race(tasks);

        assertEquals(of("2000"), env.balanceOf(a).add(env.balanceOf(b)));
        assertEquals(of("1000"), env.balanceOf(a), "equal traffic in both directions nets out");
        assertLedgerConsistent();
    }

    @RepeatedTest(3)
    void a_random_storm_of_transfers_among_many_accounts_conserves_money_and_never_goes_negative() throws Exception {
        int accounts = 8;
        var numbers = new ArrayList<String>();
        for (int i = 0; i < accounts; i++) {
            numbers.add(env.accountWithBalance("c" + i, "50"));
        }
        var expectedTotal = of("400");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            int seed = t;
            tasks.add(() -> {
                var random = new java.util.Random(seed);
                for (int n = 0; n < 40; n++) {
                    int from = random.nextInt(accounts);
                    int to = (from + 1 + random.nextInt(accounts - 1)) % accounts;
                    try {
                        env.accounts.transfer(numbers.get(from), numbers.get(to),
                                BigDecimal.valueOf(1 + random.nextInt(60)));
                    } catch (DomainException e) {
                        assertInstanceOf(InsufficientFunds.class, e);
                    }
                }
                return null;
            });
        }

        race(tasks);

        for (var number : numbers) {
            assertTrue(env.balanceOf(number).signum() >= 0, "negative balance on " + number);
        }
        assertEquals(0, expectedTotal.compareTo(env.totalOfAllBalances()), "money must be conserved");
        assertLedgerConsistent();
    }

    @Test
    void mixed_deposits_and_withdrawals_keep_the_ledger_and_balance_in_agreement() throws Exception {
        var account = env.accountWithBalance("111", "500");
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            var deposit = i % 2 == 0;
            tasks.add(() -> {
                for (int n = 0; n < 25; n++) {
                    try {
                        if (deposit) {
                            env.accounts.deposit(account, of("3"));
                        } else {
                            env.accounts.withdraw(account, of("2"));
                        }
                    } catch (DomainException e) {
                        assertInstanceOf(InsufficientFunds.class, e);
                    }
                }
                return null;
            });
        }

        race(tasks);

        // Outcome depends on interleaving, so assert invariants rather than an exact figure.
        assertTrue(env.balanceOf(account).signum() >= 0);
        assertLedgerConsistent();
        var last = env.accounts.statement(account, 1).entries().getFirst();
        assertEquals(0, last.balanceAfter().value().compareTo(env.balanceOf(account)),
                "the newest ledger entry must carry the final balance");
    }

    @Test
    void concurrent_creation_of_the_same_customer_yields_exactly_one_winner() throws Exception {
        var created = new AtomicInteger();
        var conflicts = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    env.customers.create("same", "Same Person");
                    created.incrementAndGet();
                } catch (DomainException e) {
                    assertInstanceOf(Conflict.class, e);
                    conflicts.incrementAndGet();
                }
                return null;
            });
        }

        race(tasks);

        assertEquals(1, created.get());
        assertEquals(THREADS - 1, conflicts.get());
        assertEquals(1, env.customers.search(null).size());
    }
}
