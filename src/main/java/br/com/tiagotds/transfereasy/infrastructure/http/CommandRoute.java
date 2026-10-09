package br.com.tiagotds.transfereasy.infrastructure.http;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.application.pipeline.CommandBus;
import br.com.tiagotds.transfereasy.application.pipeline.CommandContext;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The one shape every write endpoint has: parse a JSON body of type {@code B}, turn it into a {@code Command<R>},
 * dispatch it with the request's {@code Idempotency-Key}, and answer {@code 201} with the result mapped to a DTO.
 */
final class CommandRoute {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private CommandRoute() {
    }

    static <B, R> HttpRoute of(CommandBus bus, Class<B> bodyType,
                               BiFunction<HttpRequest, B, ? extends Command<R>> toCommand,
                               Function<? super R, ?> toResponse) {
        return request -> {
            var command = toCommand.apply(request, body(request, bodyType));
            var outcome = bus.dispatch(command, CommandContext.withIdempotencyKey(request.header(IDEMPOTENCY_KEY)));
            var response = HttpResponse.created(toResponse.apply(outcome.value()));
            return outcome.replayed() ? response.withHeader(REPLAYED, "true") : response;
        };
    }

    static <B> B body(HttpRequest request, Class<B> type) {
        try {
            return Json.read(request.body(), type);
        } catch (Json.InvalidJsonException e) {
            throw new InvalidInput(e.getMessage());
        }
    }
}
