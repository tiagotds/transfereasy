package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Drives every JDBC failure mode with a scripted connection, proving the connection is always closed. */
class TransactionRunnerFailureTest {

    /**
     * A Connection that records every call and throws SQLException for scripted ones. An entry is either a
     * method name (every call fails) or {@code name:N} (only the Nth call of that method fails).
     */
    private static final class Script {
        final List<String> calls = new ArrayList<>();
        final Set<String> failing;
        final java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        Script(String... failing) {
            this.failing = Set.of(failing);
        }

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        var name = method.getName();
                        calls.add(name);
                        int nth = counts.merge(name, 1, Integer::sum);
                        if (failing.contains(name) || failing.contains(name + ":" + nth)) {
                            throw new SQLException("scripted failure of " + method.getName());
                        }
                        return switch (method.getReturnType().getName()) {
                            case "boolean" -> false;
                            case "int" -> 0;
                            default -> null;
                        };
                    });
        }

        TransactionRunner runner() {
            return new TransactionRunner(this::connection);
        }
    }

    @Test
    void failure_to_obtain_a_connection_is_wrapped() {
        var runner = new TransactionRunner(() -> {
            throw new SQLException("pool exhausted");
        });

        var e = assertThrows(DatabaseException.class, () -> runner.inTransaction(() -> null));

        assertEquals("Could not obtain a database connection", e.getMessage());
        assertEquals("pool exhausted", e.getCause().getMessage());
    }

    @Test
    void failure_to_begin_is_wrapped_and_the_connection_is_still_closed() {
        var script = new Script("setAutoCommit");

        var e = assertThrows(DatabaseException.class, () -> script.runner().inTransaction(() -> null));

        assertEquals("Could not begin transaction", e.getMessage());
        assertTrue(script.calls.contains("close"));
    }

    @Test
    void failure_to_commit_is_wrapped_rolled_back_and_the_connection_closed() {
        var script = new Script("commit");

        var e = assertThrows(DatabaseException.class, () -> script.runner().inTransaction(() -> null));

        assertEquals("Could not commit transaction", e.getMessage());
        assertTrue(script.calls.contains("rollback"));
        assertTrue(script.calls.contains("close"));
    }

    @Test
    void failure_to_rollback_is_suppressed_so_the_original_error_wins() {
        var script = new Script("rollback");
        var original = new IllegalStateException("original");

        var thrown = assertThrows(IllegalStateException.class, () -> script.runner().inTransaction(() -> {
            throw original;
        }));

        assertSame(original, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(script.calls.contains("close"));
    }

    @Test
    void failure_to_restore_defaults_still_closes_the_connection() {
        var script = new Script("setReadOnly:2");

        assertEquals("ok", script.runner().inTransaction(() -> "ok"));

        assertTrue(script.calls.contains("close"));
    }

    @Test
    void failure_to_close_does_not_mask_the_result() {
        var script = new Script("close");

        assertEquals("ok", script.runner().inTransaction(() -> "ok"));
    }

    @Test
    void the_read_only_flavour_uses_a_read_only_repeatable_read_transaction() {
        var script = new Script();

        script.runner().inReadOnlyTransaction(() -> null);

        assertTrue(script.calls.indexOf("setTransactionIsolation") < script.calls.indexOf("commit"));
        assertTrue(script.calls.contains("setReadOnly"));
    }

    @Test
    void database_exception_keeps_its_cause() {
        var cause = new SQLException("x");

        assertSame(cause, new DatabaseException("m", cause).getCause());
    }
}
