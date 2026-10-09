package br.com.tiagotds.transfereasy.domain.error;

/** The client sent an Idempotency-Key it had already used for a request with different content. */
public final class IdempotencyKeyReused extends DomainException {

    public IdempotencyKeyReused(String key) {
        super("Idempotency-Key '" + key + "' was already used for a different request.");
    }
}
