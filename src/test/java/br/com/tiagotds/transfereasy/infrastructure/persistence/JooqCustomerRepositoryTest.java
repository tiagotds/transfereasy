package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import org.junit.jupiter.api.Test;

class JooqCustomerRepositoryTest extends RepositoryTestBase {

    private final JooqCustomerRepository customers = new JooqCustomerRepository();

    private Customer add(String tax, String name) {
        return tx(() -> customers.add(TaxNumber.of(tax), CustomerName.of(name), NOW));
    }

    @Test
    void an_added_customer_gets_an_id_and_can_be_found_by_tax_number() {
        var added = add("1", "Ada Lovelace");

        var found = tx(() -> customers.find(TaxNumber.of("1"))).orElseThrow();

        assertTrue(added.id() > 0);
        assertEquals(added, found);
        assertTrue(tx(() -> customers.find(TaxNumber.of("2"))).isEmpty());
    }

    @Test
    void a_duplicate_tax_number_is_a_conflict_and_the_unique_constraint_is_the_arbiter() {
        add("1", "Ada");

        assertThrows(Conflict.class, () -> add("1", "Someone else"));
    }

    @Test
    void search_is_case_insensitive_ordered_by_creation_and_blank_matches_everyone() {
        add("1", "Ada Lovelace");
        add("2", "Alan Turing");

        assertEquals(1, tx(() -> customers.search("LOVE")).size());
        assertEquals("1", tx(() -> customers.search("a")).getFirst().taxNumber().value());
        assertEquals(2, tx(() -> customers.search(null)).size());
        assertEquals(2, tx(() -> customers.search("  ")).size());
    }
}
