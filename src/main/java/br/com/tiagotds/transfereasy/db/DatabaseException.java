package br.com.tiagotds.transfereasy.db;

/** Unrecoverable infrastructure failure (connection, commit, rollback...). */
public final class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
