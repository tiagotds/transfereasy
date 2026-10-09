package br.com.tiagotds.transfereasy.domain.error;

/**
 * A business rule said no. The hierarchy is sealed so the HTTP edge maps every outcome exhaustively, and these
 * exceptions skip stack-trace capture: they are expected results, not bugs, and are thrown on hot paths.
 */
public abstract sealed class DomainException extends RuntimeException
        permits InvalidInput, NotFound, Conflict, InsufficientFunds {

    protected DomainException(String message) {
        super(message, null, false, false);
    }
}
