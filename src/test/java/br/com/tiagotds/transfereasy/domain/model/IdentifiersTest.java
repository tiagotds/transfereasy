package br.com.tiagotds.transfereasy.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void text_values_are_trimmed() {
        assertEquals("123", TaxNumber.of(" 123 ").value());
        assertEquals("Ada Lovelace", CustomerName.of(" Ada Lovelace ").value());
        assertEquals("abc", AccountNumber.of(" abc ", "toAccountNumber").value());
    }

    @Test
    void blank_or_missing_values_are_rejected_naming_the_field() {
        assertEquals("Field 'taxNumber' is required.", messageOf(() -> TaxNumber.of(null)));
        assertEquals("Field 'name' is required.", messageOf(() -> CustomerName.of("  ")));
        assertEquals("Field 'toAccountNumber' is required.", messageOf(() -> AccountNumber.of("", "toAccountNumber")));
    }

    @Test
    void over_long_values_are_rejected_with_the_limit() {
        assertEquals("Field 'taxNumber' must have at most 32 characters.",
                messageOf(() -> TaxNumber.of("x".repeat(33))));
        assertEquals("Field 'name' must have at most 120 characters.",
                messageOf(() -> CustomerName.of("x".repeat(121))));
    }

    @Test
    void the_canonical_constructor_enforces_the_same_invariants() {
        assertThrows(InvalidInput.class, () -> new TaxNumber(" "));
        assertThrows(InvalidInput.class, () -> new AccountNumber(null));
    }

    @Test
    void generated_account_numbers_are_32_hex_characters() {
        var number = AccountNumber.generate(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

        assertEquals("123e4567e89b12d3a456426614174000", number.value());
    }

    @Test
    void identifiers_render_as_their_value() {
        assertEquals("abc", AccountNumber.of("abc").toString());
    }

    private static String messageOf(Runnable action) {
        return assertThrows(InvalidInput.class, action::run).getMessage();
    }
}
