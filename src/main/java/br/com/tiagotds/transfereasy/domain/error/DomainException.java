package br.com.tiagotds.transfereasy.domain.error;

/**
 * A business rule said no. The hierarchy is sealed so the HTTP edge maps every outcome exhaustively, and these
 * exceptions skip stack-trace capture: they are expected results, not bugs, and are thrown on hot paths. A cause
 * may still be attached (e.g. the database error behind an exhausted retry).
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidInput, NotFound, Conflict, InsufficientFunds, ConcurrentModification,
        IdempotencyKeyReused {

    protected DomainException(String message) {
        this(message, null);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause, true, false);
    }
}
