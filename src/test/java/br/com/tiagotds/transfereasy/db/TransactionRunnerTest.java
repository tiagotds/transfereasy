package br.com.tiagotds.transfereasy.db;

import static br.com.tiagotds.transfereasy.jooq.Tables.CUSTOMERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
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
        database = Database.startInMemory("tx-" + UUID.randomUUID(), 1);
        runner = new TransactionRunner(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private static void insertCustomer(DSLContext db, String tax) {
        db.insertInto(CUSTOMERS).set(CUSTOMERS.TAX_NUMBER, tax).set(CUSTOMERS.NAME, "n")
                .set(CUSTOMERS.CREATED_AT, OffsetDateTime.now()).execute();
    }

    private int customerCount() {
        return runner.inReadOnlyTransaction(db -> db.fetchCount(CUSTOMERS));
    }

    @Test
    void committed_work_is_visible_afterwards() {
        runner.inTransaction(db -> {
            insertCustomer(db, "1");
            return null;
        });

        assertEquals(1, customerCount());
    }

    @Test
    void a_runtime_exception_rolls_back_and_releases_the_connection() {
        var boom = new IllegalStateException("boom");

        var thrown = assertThrows(IllegalStateException.class, () -> runner.inTransaction(db -> {
            insertCustomer(db, "1");
            throw boom;
        }));

        assertSame(boom, thrown);
        assertTimeoutPreemptively(LIMIT, () -> assertEquals(0, customerCount()));
    }

    @Test
    void an_error_which_is_not_an_exception_still_rolls_back_and_releases_the_connection() {
        var thrown = assertThrows(OutOfMemoryError.class, () -> runner.inTransaction(db -> {
            insertCustomer(db, "1");
            throw new OutOfMemoryError("simulated");
        }));

        assertEquals("simulated", thrown.getMessage());
        assertTimeoutPreemptively(LIMIT, () -> assertEquals(0, customerCount()));
    }

    @Test
    void repeated_failures_never_exhaust_the_pool() {
        assertTimeoutPreemptively(LIMIT, () -> {
            for (int i = 0; i < 50; i++) {
                assertThrows(AssertionError.class, () -> runner.inTransaction(db -> {
                    insertCustomer(db, "x");
                    throw new AssertionError("fail");
                }));
            }
            assertEquals(0, customerCount());
        });
    }

    @Test
    void a_failed_commit_by_constraint_violation_is_rolled_back() {
        runner.inTransaction(db -> {
            insertCustomer(db, "1");
            return null;
        });

        assertThrows(org.jooq.exception.DataAccessException.class, () -> runner.inTransaction(db -> {
            insertCustomer(db, "2");
            insertCustomer(db, "1"); // unique violation
            return null;
        }));

        assertTimeoutPreemptively(LIMIT, () -> assertEquals(1, customerCount()));
    }

    @Test
    void a_failing_read_only_transaction_does_not_poison_later_writers() {
        assertThrows(IllegalStateException.class, () -> runner.inReadOnlyTransaction(db -> {
            db.fetchCount(CUSTOMERS);
            throw new IllegalStateException("boom");
        }));

        runner.inTransaction(db -> {
            insertCustomer(db, "2");
            return null;
        });

        assertEquals(1, customerCount());
    }

    @Test
    void failing_to_obtain_a_connection_is_reported_as_a_database_exception() {
        database.close();

        assertThrows(RuntimeException.class, () -> runner.inTransaction(db -> null));
    }
}
