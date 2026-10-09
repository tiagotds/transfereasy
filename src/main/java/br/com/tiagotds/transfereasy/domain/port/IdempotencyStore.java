package br.com.tiagotds.transfereasy.domain.port;

import java.util.Optional;

/**
 * Remembers the result of operations sent with an Idempotency-Key. All methods run inside the caller's current
 * transaction, the same one as the operation itself, so the result is remembered if and only if the operation
 * committed.
 */
public interface IdempotencyStore {

    /** A completed operation: what was asked (fingerprint) and what it produced. */
    record StoredResult(String fingerprint, String type, String json) {
    }

    /** Only committed, hence completed, operations are visible. */
    Optional<StoredResult> find(String key);

    /**
     * Reserves {@code key} for the operation about to run. When another transaction holds or committed the same
     * key, this fails with a retryable error: on retry, {@link #find} sees the winner's result.
     */
    void claim(String key, String fingerprint);

    void complete(String key, StoredResult result);
}
