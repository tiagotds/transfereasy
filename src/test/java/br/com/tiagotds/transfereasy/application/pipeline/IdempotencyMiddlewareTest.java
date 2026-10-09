package br.com.tiagotds.transfereasy.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.application.command.DepositMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.error.IdempotencyKeyReused;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.port.IdempotencyStore;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyMiddlewareTest {

    private static final AccountNumber A = AccountNumber.of("A");
    private static final DepositMoney DEPOSIT_10 = new DepositMoney(A, new BigDecimal("10"));
    private static final CommandContext KEY_1 = CommandContext.withIdempotencyKey("k-1");

    /** In-memory store; the real one is transactional and arbitrates races with a primary key. */
    static final class MapStore implements IdempotencyStore {
        final Map<String, String> claims = new HashMap<>();
        final Map<String, StoredResult> rows = new HashMap<>();

        @Override
        public Optional<StoredResult> find(String key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public void claim(String key, String fingerprint) {
            if (claims.putIfAbsent(key, fingerprint) != null) {
                throw new IllegalStateException("duplicate key " + key);
            }
        }

        @Override
        public void complete(String key, StoredResult result) {
            rows.put(key, result);
        }
    }

    private final MapStore store = new MapStore();
    private final IdempotencyMiddleware middleware = new IdempotencyMiddleware(store, new ResultCodec(String.class));
    private final AtomicInteger executions = new AtomicInteger();

    private Outcome<?> send(Object command, CommandContext context, String result) {
        return middleware.around((br.com.tiagotds.transfereasy.application.command.Command<?>) command, context,
                () -> {
                    executions.incrementAndGet();
                    return Outcome.fresh(result);
                });
    }

    @Test
    void without_a_key_every_request_executes() {
        send(DEPOSIT_10, CommandContext.NONE, "r");
        send(DEPOSIT_10, CommandContext.NONE, "r");

        assertEquals(2, executions.get());
        assertTrue(store.rows.isEmpty());
    }

    @Test
    void the_first_request_with_a_key_executes_and_its_result_is_remembered() {
        var outcome = send(DEPOSIT_10, KEY_1, "first");

        assertFalse(outcome.replayed());
        assertEquals("first", outcome.value());
        assertTrue(store.rows.containsKey("k-1"));
    }

    @Test
    void a_repeat_with_the_same_key_and_command_replays_the_stored_result_without_executing() {
        send(DEPOSIT_10, KEY_1, "first");

        var replay = send(new DepositMoney(A, new BigDecimal("10.00")), KEY_1, "second");

        assertEquals(1, executions.get());
        assertTrue(replay.replayed());
        assertEquals("first", replay.value());
    }

    @Test
    void reusing_a_key_for_a_different_command_is_refused() {
        send(DEPOSIT_10, KEY_1, "first");

        assertThrows(IdempotencyKeyReused.class,
                () -> send(new DepositMoney(A, new BigDecimal("11")), KEY_1, "x"));
        assertThrows(IdempotencyKeyReused.class,
                () -> send(new WithdrawMoney(A, new BigDecimal("10")), KEY_1, "x"));
        assertEquals(1, executions.get());
    }

    @Test
    void the_key_is_claimed_before_the_handler_runs_so_a_concurrent_duplicate_collides_early() {
        var claimedBeforeExecution = new java.util.ArrayList<Boolean>();

        middleware.around(DEPOSIT_10, KEY_1, () -> {
            claimedBeforeExecution.add(store.claims.containsKey("k-1"));
            return Outcome.fresh("r");
        });

        assertEquals(java.util.List.of(true), claimedBeforeExecution);
    }

    @Test
    void a_failed_execution_is_never_completed() {
        assertThrows(IllegalStateException.class, () -> middleware.around(DEPOSIT_10, KEY_1, () -> {
            throw new IllegalStateException("boom");
        }));

        assertTrue(store.rows.isEmpty(), "and the real store's claim is rolled back with the transaction");
    }

    @Test
    void every_result_type_survives_the_round_trip() {
        var codec = new ResultCodec();
        var account = br.com.tiagotds.transfereasy.domain.model.Account.open(A, 1,
                java.time.OffsetDateTime.parse("2026-01-15T10:00:00Z"));
        var customer = new br.com.tiagotds.transfereasy.domain.model.Customer(1,
                br.com.tiagotds.transfereasy.domain.model.TaxNumber.of("1"),
                br.com.tiagotds.transfereasy.domain.model.CustomerName.of("Ada"), account.createdAt());
        var receipt = new br.com.tiagotds.transfereasy.domain.model.TransferReceipt("t", account, account);

        for (Object value : java.util.List.of(account, customer, receipt)) {
            assertEquals(value, codec.decode(codec.encode("fp", value)));
        }
    }

    @Test
    void an_unknown_result_type_cannot_be_stored() {
        assertThrows(IllegalArgumentException.class, () -> new ResultCodec().encode("fp", "a string"));
        assertThrows(IllegalArgumentException.class,
                () -> new ResultCodec().decode(new IdempotencyStore.StoredResult("fp", "Nope", "{}")));
    }

    @Test
    void the_fingerprint_is_stable_for_equal_commands_and_differs_otherwise() {
        var codec = new ResultCodec();

        assertEquals(codec.fingerprint(DEPOSIT_10), codec.fingerprint(new DepositMoney(A, new BigDecimal("10.0"))));
        assertFalse(codec.fingerprint(DEPOSIT_10).equals(codec.fingerprint(new WithdrawMoney(A, BigDecimal.TEN))));
        assertEquals(64, codec.fingerprint(DEPOSIT_10).length(), "hex SHA-256");
    }
}
