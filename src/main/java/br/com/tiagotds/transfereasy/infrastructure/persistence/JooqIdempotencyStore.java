package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static br.com.tiagotds.transfereasy.jooq.Tables.IDEMPOTENCY_KEYS;

import br.com.tiagotds.transfereasy.domain.error.Retryable;
import br.com.tiagotds.transfereasy.domain.port.IdempotencyStore;
import br.com.tiagotds.transfereasy.jooq.tables.records.IdempotencyKeysRecord;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jooq.exception.IntegrityConstraintViolationException;

public final class JooqIdempotencyStore extends JooqRepository<IdempotencyKeysRecord, IdempotencyStore.StoredResult>
        implements IdempotencyStore {

    /** Another request with the same key got there first; retrying will replay its result. */
    static final class KeyInUse extends RuntimeException implements Retryable {
        KeyInUse(String key, Throwable cause) {
            super("Idempotency-Key '" + key + "' is being used by a concurrent request", cause);
        }
    }

    private final Supplier<OffsetDateTime> now;

    public JooqIdempotencyStore(Supplier<OffsetDateTime> now) {
        super(IDEMPOTENCY_KEYS);
        this.now = now;
    }

    @Override
    public Optional<StoredResult> find(String key) {
        return findOne(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key));
    }

    @Override
    public void claim(String key, String fingerprint) {
        try {
            db().insertInto(IDEMPOTENCY_KEYS)
                    .set(Map.of(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, key, IDEMPOTENCY_KEYS.FINGERPRINT, fingerprint,
                            IDEMPOTENCY_KEYS.CREATED_AT, now.get()))
                    .execute();
        } catch (IntegrityConstraintViolationException e) {
            throw new KeyInUse(key, e);
        }
    }

    @Override
    public void complete(String key, StoredResult result) {
        db().update(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.RESULT_TYPE, result.type())
                .set(IDEMPOTENCY_KEYS.RESULT_JSON, result.json())
                .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
                .execute();
    }

    @Override
    protected StoredResult toDomain(IdempotencyKeysRecord r) {
        return new StoredResult(r.getFingerprint(), r.getResultType(), r.getResultJson());
    }
}
