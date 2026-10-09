package br.com.tiagotds.transfereasy.application.scenarios;

import static br.com.tiagotds.transfereasy.support.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.application.pipeline.CommandContext;
import br.com.tiagotds.transfereasy.application.pipeline.Outcome;
import br.com.tiagotds.transfereasy.domain.error.IdempotencyKeyReused;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;
import br.com.tiagotds.transfereasy.support.Race;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class IdempotencyScenariosTest {

    private static final int THREADS = 32;
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

    private Outcome<Account> withdraw(String account, String amount, String key) {
        return env.core.commands().dispatch(new WithdrawMoney(AccountNumber.of(account), of(amount)),
                CommandContext.withIdempotencyKey(key));
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_retried_withdrawal_is_applied_once_and_the_retry_sees_the_original_result(LockingStrategy strategy) {
        start(strategy);
        var account = env.accountWithBalance("1", "100");

        var first = withdraw(account, "30", "k-1");
        var retry = withdraw(account, "30", "k-1");

        assertEquals(of("70"), env.balanceOf(account));
        assertTrue(retry.replayed());
        assertEquals(first.value(), retry.value());
        assertEquals(2, env.statement(account, null).entries().size(), "deposit + ONE withdrawal");
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void different_keys_are_different_operations(LockingStrategy strategy) {
        start(strategy);
        var account = env.accountWithBalance("1", "100");

        withdraw(account, "30", "k-1");
        withdraw(account, "30", "k-2");

        assertEquals(of("40"), env.balanceOf(account));
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_key_reused_for_another_request_is_refused_and_changes_nothing(LockingStrategy strategy) {
        start(strategy);
        var account = env.accountWithBalance("1", "100");
        withdraw(account, "30", "k-1");

        assertThrows(IdempotencyKeyReused.class, () -> withdraw(account, "31", "k-1"));

        assertEquals(of("70"), env.balanceOf(account));
    }

    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_refused_request_is_not_remembered_so_the_same_key_works_once_funds_arrive(LockingStrategy strategy) {
        start(strategy);
        var account = env.accountWithBalance("1", "10");
        assertThrows(InsufficientFunds.class, () -> withdraw(account, "30", "k-1"));

        env.deposit(account, of("50"));

        assertEquals(of("30"), withdraw(account, "30", "k-1").value().balance().value());
    }

    /**
     * The client's network times out and it fires the same request 32 times at once. Exactly one execution may
     * happen and all 32 callers must receive that one result.
     */
    @ParameterizedTest
    @EnumSource(LockingStrategy.class)
    void a_storm_of_identical_concurrent_requests_executes_exactly_once(LockingStrategy strategy) throws Exception {
        start(strategy);
        var from = env.accountWithBalance("1", "100");
        var to = env.accountWithBalance("2", "0");
        var command = new TransferMoney(AccountNumber.of(from), AccountNumber.of(to), of("25"));

        var outcomes = Race.run(THREADS, i -> () ->
                env.core.commands().dispatch(command, CommandContext.withIdempotencyKey("storm")));

        assertEquals(of("75"), env.balanceOf(from));
        assertEquals(of("25"), env.balanceOf(to));
        var transferIds = new HashSet<String>();
        outcomes.forEach(o -> transferIds.add(((TransferReceipt) o.value()).transferId()));
        assertEquals(1, transferIds.size(), "every caller sees the same transfer");
        assertEquals(1, outcomes.stream().filter(o -> !o.replayed()).count(), "exactly one fresh execution");
        assertEquals(2, env.statement(from, null).entries().size(), "the initial deposit + ONE transfer out");
        assertEquals(1, env.statement(to, null).entries().size(), "ONE transfer in");
    }
}
