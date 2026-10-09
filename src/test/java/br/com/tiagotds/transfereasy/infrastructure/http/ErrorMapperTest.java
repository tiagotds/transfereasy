package br.com.tiagotds.transfereasy.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.IdempotencyKeyReused;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErrorMapperTest {

    private static void assertMaps(Throwable failure, int status, String code, String message) {
        var response = ErrorMapper.toResponse(failure);

        assertEquals(status, response.status());
        assertEquals(new ErrorBody(code, message), response.body());
    }

    @Test
    void every_domain_outcome_has_a_stable_status_and_code() {
        assertMaps(new InvalidInput("bad"), 400, "INVALID_REQUEST", "bad");
        assertMaps(NotFound.customer(), 404, "NOT_FOUND", "Customer not found.");
        assertMaps(Conflict.duplicateCustomer(TaxNumber.of("1")), 409, "CONFLICT",
                "A customer with tax number '1' already exists.");
        assertMaps(new InsufficientFunds(), 422, "INSUFFICIENT_FUNDS", "Insufficient funds for this operation.");
        assertMaps(ConcurrentModification.of(AccountNumber.of("a")), 409, "CONCURRENT_MODIFICATION",
                "Account 'a' was modified concurrently, please retry.");
        assertMaps(new IdempotencyKeyReused("k"), 422, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key 'k' was already used for a different request.");
    }

    @Test
    void anything_unexpected_including_errors_is_a_generic_500_that_leaks_nothing() {
        assertMaps(new IllegalStateException("secret"), 500, "INTERNAL_ERROR", "Unexpected error.");
        assertMaps(new StackOverflowError("secret"), 500, "INTERNAL_ERROR", "Unexpected error.");
    }

    @Test
    void routing_failures_have_their_own_responses() {
        assertEquals(404, ErrorMapper.noRoute().status());
        var notAllowed = ErrorMapper.methodNotAllowed(Set.of("POST", "GET"));
        assertEquals(405, notAllowed.status());
        assertEquals(Map.of("Allow", "GET, POST"), notAllowed.headers());
        assertEquals(413, ErrorMapper.payloadTooLarge().status());
    }
}
