package br.com.tiagotds.transfereasy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.DomainException;
import br.com.tiagotds.transfereasy.domain.DomainException.Code;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {

    private TestEnvironment env;

    @BeforeEach
    void setUp() {
        env = new TestEnvironment();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private Code codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }

    @Test
    void a_customer_can_be_created_and_fetched_by_tax_number() {
        var created = env.customers.create(" 123 ", " Ada Lovelace ");

        var fetched = env.customers.get("123");

        assertEquals("123", created.taxNumber());
        assertEquals("Ada Lovelace", fetched.name());
    }

    @Test
    void a_duplicate_tax_number_is_a_conflict() {
        env.customers.create("123", "Ada");

        assertEquals(Code.CONFLICT, codeOf(() -> env.customers.create("123", "Someone else")));
    }

    @Test
    void blank_or_missing_fields_are_invalid() {
        assertEquals(Code.INVALID, codeOf(() -> env.customers.create(null, "Ada")));
        assertEquals(Code.INVALID, codeOf(() -> env.customers.create("1", " ")));
    }

    @Test
    void over_long_fields_are_invalid() {
        assertEquals(Code.INVALID, codeOf(() -> env.customers.create("x".repeat(33), "Ada")));
        assertEquals(Code.INVALID, codeOf(() -> env.customers.create("1", "x".repeat(121))));
    }

    @Test
    void an_unknown_customer_is_not_found() {
        assertEquals(Code.NOT_FOUND, codeOf(() -> env.customers.get("ghost")));
        assertEquals(Code.NOT_FOUND, codeOf(() -> env.customers.accountsOf("ghost")));
    }

    @Test
    void search_matches_names_case_insensitively_and_blank_returns_everyone() {
        env.customers.create("1", "Ada Lovelace");
        env.customers.create("2", "Alan Turing");

        assertEquals(1, env.customers.search("LOVE").size());
        assertEquals(2, env.customers.search("a").size());
        assertEquals(2, env.customers.search(null).size());
        assertEquals(2, env.customers.search("  ").size());
        assertEquals(0, env.customers.search("zzz").size());
    }

    @Test
    void accounts_of_a_customer_without_accounts_is_empty() {
        env.customers.create("1", "Ada");

        assertEquals(0, env.customers.accountsOf("1").size());
    }
}
