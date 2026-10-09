package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LockingStrategyTest extends RepositoryTestBase {

    private final JooqAccountRepository accounts = new JooqAccountRepository();

    @BeforeEach
    void twoAccounts() {
        var customerId = tx(() -> new JooqCustomerRepository().add(TaxNumber.of("1"), CustomerName.of("A"), NOW)).id();
        tx(() -> accounts.add(Account.open(AccountNumber.of("B"), customerId, NOW)));
        tx(() -> accounts.add(Account.open(AccountNumber.of("A"), customerId, NOW)));
    }

    @Test
    void both_strategies_load_existing_accounts_in_ascending_id_order() {
        var numbers = List.of(AccountNumber.of("A"), AccountNumber.of("ghost"), AccountNumber.of("B"));
        for (var strategy : LockingStrategy.values()) {
            var loaded = tx(() -> strategy.load(accounts, numbers));

            assertEquals(List.of("B", "A"), loaded.stream().map(a -> a.number().value()).toList(), strategy.name());
        }
    }

    @Test
    void the_strategy_is_chosen_from_configuration() {
        assertEquals(LockingStrategy.PESSIMISTIC, LockingStrategy.valueOf("PESSIMISTIC"));
        assertEquals(2, LockingStrategy.values().length);
    }

    /**
     * The difference between the strategies, observed directly: two transactions read the same account, then both
     * try to write. Pessimistic makes the second reader wait for the first writer, so both succeed in turn;
     * optimistic lets both read concurrently, and the second write loses the compare-and-set.
     */
    @Test
    void optimistic_readers_do_not_block_and_the_slower_writer_loses() throws Exception {
        var bothRead = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> writeAfterBothRead(LockingStrategy.OPTIMISTIC, bothRead, "1"));
            var second = pool.submit(() -> writeAfterBothRead(LockingStrategy.OPTIMISTIC, bothRead, "2"));
            var outcomes = List.of(outcome(first), outcome(second));

            assertEquals(1, outcomes.stream().filter("ok"::equals).count(), outcomes.toString());
            assertEquals(1, outcomes.stream().filter("conflict"::equals).count(), outcomes.toString());
        }
    }

    @Test
    void pessimistic_readers_queue_so_neither_writer_loses() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> lockedDeposit("1"));
            var second = pool.submit(() -> lockedDeposit("2"));

            assertEquals("ok", outcome(first));
            assertEquals("ok", outcome(second));
        }
        assertEquals(Money.of("3"), tx(() -> accounts.find(AccountNumber.of("A"))).orElseThrow().balance());
    }

    private String writeAfterBothRead(LockingStrategy strategy, CountDownLatch bothRead, String amount) {
        try {
            run(() -> {
                var account = strategy.load(accounts, List.of(AccountNumber.of("A"))).getFirst();
                bothRead.countDown();
                await(bothRead);
                accounts.save(account.deposit(Money.of(amount), NOW).account());
            });
            return "ok";
        } catch (ConcurrentModification e) {
            return "conflict";
        }
    }

    private String lockedDeposit(String amount) {
        run(() -> {
            var account = LockingStrategy.PESSIMISTIC.load(accounts, List.of(AccountNumber.of("A"))).getFirst();
            accounts.save(account.deposit(Money.of(amount), NOW).account());
        });
        return "ok";
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for the other reader");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static String outcome(java.util.concurrent.Future<String> future) throws Exception {
        return future.get(20, TimeUnit.SECONDS);
    }
}
