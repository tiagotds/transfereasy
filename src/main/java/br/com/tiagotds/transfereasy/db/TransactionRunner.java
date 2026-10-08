package br.com.tiagotds.transfereasy.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

/**
 * The only place that opens, commits, rolls back and closes connections.
 *
 * <p>Guarantee: whatever the work throws (checked, unchecked or {@link Error}), the transaction is
 * rolled back and the connection returned to the pool. Callers never manage transactions themselves.
 */
public final class TransactionRunner {

    private static final Settings SETTINGS = new Settings().withRenderSchema(false);

    /** Where connections come from; a seam so failure paths can be exercised in tests. */
    @FunctionalInterface
    public interface ConnectionSource {
        Connection get() throws SQLException;
    }

    private final ConnectionSource connections;

    public TransactionRunner(Database database) {
        this(database::connection);
    }

    public TransactionRunner(ConnectionSource connections) {
        this.connections = connections;
    }

    /** Read-committed read/write transaction. Writers rely on row locks and atomic conditional updates. */
    public <T> T inTransaction(Function<DSLContext, T> work) {
        return run(work, Connection.TRANSACTION_READ_COMMITTED, false);
    }

    /** Snapshot (repeatable-read) read-only transaction: multiple queries see one consistent state. */
    public <T> T inReadOnlyTransaction(Function<DSLContext, T> work) {
        return run(work, Connection.TRANSACTION_REPEATABLE_READ, true);
    }

    private <T> T run(Function<DSLContext, T> work, int isolation, boolean readOnly) {
        Connection connection = open();
        try {
            begin(connection, isolation, readOnly);
            T result = work.apply(DSL.using(connection, SQLDialect.H2, SETTINGS));
            commit(connection);
            return result;
        } catch (Throwable failure) {
            rollbackQuietly(connection, failure);
            throw failure;
        } finally {
            releaseQuietly(connection);
        }
    }

    private Connection open() {
        try {
            return connections.get();
        } catch (SQLException e) {
            throw new DatabaseException("Could not obtain a database connection", e);
        }
    }

    private static void begin(Connection connection, int isolation, boolean readOnly) {
        try {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(isolation);
            connection.setReadOnly(readOnly);
        } catch (SQLException e) {
            throw new DatabaseException("Could not begin transaction", e);
        }
    }

    private static void commit(Connection connection) {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new DatabaseException("Could not commit transaction", e);
        }
    }

    private static void rollbackQuietly(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException | RuntimeException e) {
            cause.addSuppressed(e);
        }
    }

    /** Restores pool defaults so a recycled connection never inherits state, then returns it to the pool. */
    private static void releaseQuietly(Connection connection) {
        try {
            connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(true);
        } catch (SQLException | RuntimeException ignored) {
            // the connection is closed right below; a broken one is discarded by the pool
        } finally {
            try {
                connection.close();
            } catch (SQLException | RuntimeException ignored) {
                // nothing more can be done
            }
        }
    }
}
