package br.com.tiagotds.transfereasy.domain.error;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;

/** Someone else changed the aggregate between our read and our write (optimistic lock lost). */
public final class ConcurrentModification extends DomainException implements Retryable {

    private ConcurrentModification(String message, Throwable cause) {
        super(message, cause);
    }

    /** The operation kept colliding with concurrent ones (deadlock, lock timeout) until retries ran out. */
    public static ConcurrentModification retriesExhausted(Throwable lastFailure) {
        return new ConcurrentModification("The operation conflicted with concurrent requests, please retry.",
                lastFailure);
    }

    public static ConcurrentModification of(AccountNumber number) {
        return new ConcurrentModification("Account '" + number + "' was modified concurrently, please retry.", null);
    }
}
