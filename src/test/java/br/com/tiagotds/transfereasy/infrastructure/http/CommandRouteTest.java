package br.com.tiagotds.transfereasy.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.application.pipeline.CommandBus;
import br.com.tiagotds.transfereasy.application.pipeline.Outcome;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandRouteTest {

    record Body(String taxNumber) {
    }

    private static final Account OPENED = Account.open(AccountNumber.of("N1"), 1, OffsetDateTime.parse("2026-01-15T10:00:00Z"));

    private static HttpRequest post(String json, Map<String, String> headers) {
        return new HttpRequest("POST", "/x", Map.of(), json.getBytes(StandardCharsets.UTF_8), Map.of(), headers);
    }

    private static HttpRoute route(CommandBus bus) {
        return CommandRoute.of(bus, Body.class, (request, body) -> new OpenAccount(TaxNumber.of(body.taxNumber())),
                account -> account.number().value());
    }

    @Test
    void the_body_becomes_a_command_whose_result_is_returned_as_201() {
        var seen = new ArrayList<OpenAccount>();
        var bus = CommandBus.builder().handle(OpenAccount.class, command -> {
            seen.add(command);
            return OPENED;
        }).build();

        var response = route(bus).handle(post("{\"taxNumber\":\"42\"}", Map.of()));

        assertEquals(201, response.status());
        assertEquals("N1", response.body());
        assertEquals(TaxNumber.of("42"), seen.getFirst().customer());
        assertTrue(response.headers().isEmpty());
    }

    @Test
    void the_idempotency_key_header_reaches_the_pipeline_and_a_replay_is_flagged() {
        var keys = new ArrayList<String>();
        var bus = CommandBus.builder()
                .use((command, context, next) -> {
                    keys.add(context.idempotencyKey().orElseThrow());
                    return Outcome.replayed(OPENED);
                })
                .handle(OpenAccount.class, command -> OPENED)
                .build();

        var response = route(bus).handle(post("{\"taxNumber\":\"42\"}", Map.of("Idempotency-Key", "k-1")));

        assertEquals("k-1", keys.getFirst());
        assertEquals("true", response.headers().get("Idempotent-Replayed"));
    }

    @Test
    void a_malformed_body_is_invalid_input() {
        var bus = CommandBus.builder().handle(OpenAccount.class, command -> OPENED).build();

        assertThrows(InvalidInput.class, () -> route(bus).handle(post("{nope", Map.of())));
    }
}
