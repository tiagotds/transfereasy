package br.com.tiagotds.transfereasy.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.http.Dtos.AmountRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void decimals_are_parsed_exactly() throws Exception {
        var request = Json.read(bytes("{\"amount\":0.1}"), AmountRequest.class);

        assertEquals(new BigDecimal("0.1"), request.amount());
    }

    @Test
    void decimals_are_written_as_plain_numbers_never_scientific_notation() {
        var json = new String(Json.write(new AmountRequest(new BigDecimal("1E+3"))), StandardCharsets.UTF_8);

        assertEquals("{\"amount\":1000}", json);
    }

    @Test
    void empty_or_null_bodies_are_rejected() {
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(null, AmountRequest.class));
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(new byte[0], AmountRequest.class));
    }

    @Test
    void unknown_properties_duplicates_and_trailing_tokens_are_rejected() {
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(bytes("{\"amount\":1,\"x\":2}"), AmountRequest.class));
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(bytes("{\"amount\":1,\"amount\":2}"), AmountRequest.class));
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(bytes("{\"amount\":1}{}"), AmountRequest.class));
    }

    @Test
    void strings_are_not_coerced_into_numbers() {
        assertThrows(Json.InvalidJsonException.class, () -> Json.read(bytes("{\"amount\":\"5\"}"), AmountRequest.class));
    }

    @Test
    void unserialisable_values_raise_an_illegal_state() {
        assertThrows(IllegalStateException.class, () -> Json.write(new Object()));
    }
}
