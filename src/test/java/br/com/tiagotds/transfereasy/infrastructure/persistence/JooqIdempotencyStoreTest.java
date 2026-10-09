package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.Retryable;
import br.com.tiagotds.transfereasy.domain.port.IdempotencyStore.StoredResult;
import org.junit.jupiter.api.Test;

class JooqIdempotencyStoreTest extends RepositoryTestBase {

    private final JooqIdempotencyStore store = new JooqIdempotencyStore(() -> NOW);

    @Test
    void a_claimed_and_completed_key_is_found_with_its_result() {
        var result = new StoredResult("fp", "Account", "{\"x\":1}");

        run(() -> {
            store.claim("k", "fp");
            store.complete("k", result);
        });

        assertEquals(result, tx(() -> store.find("k")).orElseThrow());
        assertTrue(tx(() -> store.find("other")).isEmpty());
    }

    @Test
    void a_second_claim_of_the_same_key_loses_the_race_and_is_retryable() {
        run(() -> store.claim("k", "fp"));

        var e = assertThrows(RuntimeException.class, () -> run(() -> store.claim("k", "fp")));

        assertTrue(e instanceof Retryable, "the loser is retried, then finds the winner's row and replays it");
    }

    @Test
    void a_claim_rolled_back_with_its_transaction_leaves_the_key_free() {
        assertThrows(IllegalStateException.class, () -> run(() -> {
            store.claim("k", "fp");
            throw new IllegalStateException("handler failed");
        }));

        run(() -> store.claim("k", "fp"));
    }
}
