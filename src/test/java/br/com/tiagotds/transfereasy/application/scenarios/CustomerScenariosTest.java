package br.com.tiagotds.transfereasy.application.scenarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.DomainException;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerScenariosTest {

    private TestEnvironment env;

    @BeforeEach
    void setUp() {
        env = new TestEnvironment();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private Class<? extends DomainException> codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).getClass();
    }

    @Test
    void a_customer_can_be_created_and_fetched_by_tax_number() {
        var created = env.createCustomer(" 123 ", " Ada Lovelace ");

        var fetched = env.customer("123");

        assertEquals("123", created.taxNumber().value());
        assertEquals("Ada Lovelace", fetched.name().value());
    }

    @Test
    void a_duplicate_tax_number_is_a_conflict() {
        env.createCustomer("123", "Ada");

        assertEquals(Conflict.class, codeOf(() -> env.createCustomer("123", "Someone else")));
    }

    @Test
    void blank_or_missing_fields_are_invalid() {
        assertEquals(InvalidInput.class, codeOf(() -> env.createCustomer(null, "Ada")));
        assertEquals(InvalidInput.class, codeOf(() -> env.createCustomer("1", " ")));
    }

    @Test
    void over_long_fields_are_invalid() {
        assertEquals(InvalidInput.class, codeOf(() -> env.createCustomer("x".repeat(33), "Ada")));
        assertEquals(InvalidInput.class, codeOf(() -> env.createCustomer("1", "x".repeat(121))));
    }

    @Test
    void an_unknown_customer_is_not_found() {
        assertEquals(NotFound.class, codeOf(() -> env.customer("ghost")));
        assertEquals(NotFound.class, codeOf(() -> env.accountsOf("ghost")));
    }

    @Test
    void search_matches_names_case_insensitively_and_blank_returns_everyone() {
        env.createCustomer("1", "Ada Lovelace");
        env.createCustomer("2", "Alan Turing");

        assertEquals(1, env.search("LOVE").size());
        assertEquals(2, env.search("a").size());
        assertEquals(2, env.search(null).size());
        assertEquals(2, env.search("  ").size());
        assertEquals(0, env.search("zzz").size());
    }

    @Test
    void accounts_of_a_customer_without_accounts_is_empty() {
        env.createCustomer("1", "Ada");

        assertEquals(0, env.accountsOf("1").size());
    }
}
