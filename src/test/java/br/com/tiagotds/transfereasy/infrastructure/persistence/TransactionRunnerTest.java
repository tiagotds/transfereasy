package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static br.com.tiagotds.transfereasy.jooq.Tables.CUSTOMERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the reviewer's "TX leak on Throwable" finding. The pool holds a SINGLE connection, so any
 * leaked connection or dangling transaction makes the next call hang (and fail the timeout) instead of passing.
 */
class TransactionRunnerTest {

    private static final Duration LIMIT = Duration.ofSeconds(10);

    private Database database;
    private TransactionRunner runner;

    @BeforeEach
    void setUp() {
        database = Database.startInMemory("tx-" + UUID.randomUUID(),
                Settings.from(Config.defaults().with("db.pool-size", "1")).database());
        runner = new TransactionRunner(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static void insertCustomer(String tax) {
        TransactionRunner.current().insertInto(CUSTOMERS).set(CUSTOMERS.TAX_NUMBER, tax).set(CUSTOMERS.NAME, "n")
                .set(CUSTOMERS.CREATED_AT, OffsetDateTime.now()).execute();
    }

    private int customerCount() {
        return runner.inReadOnlyTransaction(() -> TransactionRunner.current().fetchCount(CUSTOMERS));
    }

    @Test
    void committed_work_is_visible_afterwards() {
        runner.inTransaction(() -> {
            insertCustomer("1");
            return null;
        });

        assertEquals(1, customerCount());
    }

    @Test
    void a_runtime_exception_rolls_back_and_releases_the_connection() {
        var boom = new IllegalStateException("boom");

        var thrown = assertThrows(IllegalStateException.class, () -> runner.inTransaction(() -> {
            insertCustomer("1");
            throw boom;
        }));

        assertSame(boom, thrown);
        assertTimeoutPreemptively(LIMIT, () -> assertEquals(0, customerCount()));
    }

    @Test
    void an_error_which_is_not_an_exception_still_rolls_back_and_releases_the_connection() {
        var thrown = assertThrows(OutOfMemoryError.class, () -> runner.inTransaction(() -> {
            insertCustomer("1");
            throw new OutOfMemoryError("simulated");
        }));

        assertEquals("simulated", thrown.getMessage());
        assertTimeoutPreemptively(LIMIT, () -> assertEquals(0, customerCount()));
    }

    @Test
    void repeated_failures_never_exhaust_the_pool() {
        assertTimeoutPreemptively(LIMIT, () -> {
            for (int i = 0; i < 50; i++) {
                assertThrows(AssertionError.class, () -> runner.inTransaction(() -> {
                    insertCustomer("x");
                    throw new AssertionError("fail");
                }));
            }
            assertEquals(0, customerCount());
        });
    }

    @Test
    void a_failed_commit_by_constraint_violation_is_rolled_back() {
        runner.inTransaction(() -> {
            insertCustomer("1");
            return null;
        });

        assertThrows(org.jooq.exception.DataAccessException.class, () -> runner.inTransaction(() -> {
            insertCustomer("2");
            insertCustomer("1"); // unique violation
            return null;
        }));

        assertTimeoutPreemptively(LIMIT, () -> assertEquals(1, customerCount()));
    }

    @Test
    void a_failing_read_only_transaction_does_not_poison_later_writers() {
        assertThrows(IllegalStateException.class, () -> runner.inReadOnlyTransaction(() -> {
            TransactionRunner.current().fetchCount(CUSTOMERS);
            throw new IllegalStateException("boom");
        }));

        runner.inTransaction(() -> {
            insertCustomer("2");
            return null;
        });

        assertEquals(1, customerCount());
    }

    @Test
    void the_current_transaction_is_only_visible_inside_the_work_and_is_unbound_afterwards() {
        runner.inTransaction(() -> TransactionRunner.current());

        assertThrows(IllegalStateException.class, TransactionRunner::current);
    }

    @Test
    void nested_transactions_are_refused_so_one_operation_is_always_one_transaction() {
        var e = assertThrows(IllegalStateException.class,
                () -> runner.inTransaction(() -> runner.inReadOnlyTransaction(() -> null)));

        assertEquals("A transaction is already active on this thread", e.getMessage());
        assertThrows(IllegalStateException.class, TransactionRunner::current);
    }

    @Test
    void failing_to_obtain_a_connection_is_reported_as_a_database_exception() {
        database.close();

        assertThrows(RuntimeException.class, () -> runner.inTransaction(() -> null));
    }
}
