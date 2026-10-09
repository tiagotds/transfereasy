package br.com.tiagotds.transfereasy.infrastructure.http;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.DomainException;
import br.com.tiagotds.transfereasy.domain.error.IdempotencyKeyReused;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The single place where failures become HTTP responses. Unexpected ones are logged and never leak details. */
final class ErrorMapper {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class.getName());

    private ErrorMapper() {
    }

    static HttpResponse toResponse(Throwable failure) {
        if (failure instanceof DomainException domain) {
            return fromDomain(domain);
        }
        LOG.log(Level.SEVERE, "Unhandled error while serving request", failure);
        return error(500, "INTERNAL_ERROR", "Unexpected error.");
    }

    private static HttpResponse fromDomain(DomainException e) {
        return switch (e) {
            case InvalidInput i -> error(400, "INVALID_REQUEST", i.getMessage());
            case NotFound n -> error(404, "NOT_FOUND", n.getMessage());
            case Conflict c -> error(409, "CONFLICT", c.getMessage());
            case InsufficientFunds f -> error(422, "INSUFFICIENT_FUNDS", f.getMessage());
            case ConcurrentModification m -> error(409, "CONCURRENT_MODIFICATION", m.getMessage());
            case IdempotencyKeyReused k -> error(422, "IDEMPOTENCY_KEY_REUSED", k.getMessage());
        };
    }

    static HttpResponse noRoute() {
        return error(404, "NOT_FOUND", "No such resource.");
    }

    static HttpResponse methodNotAllowed(Set<String> allowed) {
        return error(405, "METHOD_NOT_ALLOWED", "Method not allowed.")
                .withHeader("Allow", String.join(", ", new TreeSet<>(allowed)));
    }

    static HttpResponse payloadTooLarge() {
        return error(413, "PAYLOAD_TOO_LARGE", "Request body too large.");
    }

    static HttpResponse error(int status, String code, String message) {
        return new HttpResponse(status, new ErrorBody(code, message), Map.of());
    }
}
