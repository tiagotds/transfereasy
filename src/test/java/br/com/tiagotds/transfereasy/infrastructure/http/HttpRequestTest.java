package br.com.tiagotds.transfereasy.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpRequestTest {

    @Test
    void headers_are_looked_up_case_insensitively() {
        var request = new HttpRequest("GET", "/", Map.of(), new byte[0], Map.of(), Map.of("Idempotency-Key", "k"));

        assertEquals("k", request.header("idempotency-key"));
        assertNull(request.header("other"));
    }

    @Test
    void a_response_can_gain_headers_without_mutation() {
        var response = HttpResponse.ok("x");

        var withHeader = response.withHeader("A", "1");

        assertEquals(Map.of("A", "1"), withHeader.headers());
        assertEquals(Map.of(), response.headers());
    }
}
