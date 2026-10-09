package br.com.tiagotds.transfereasy.application.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandBusTest {

    private static final CreateCustomer CREATE = new CreateCustomer(TaxNumber.of("1"), CustomerName.of("Ada"));
    private static final Customer ADA = new Customer(1, TaxNumber.of("1"), CustomerName.of("Ada"),
            OffsetDateTime.parse("2026-01-15T10:00:00Z"));

    private static Middleware recording(String name, List<String> calls) {
        return (command, context, next) -> {
            calls.add(name + ">");
            var outcome = next.proceed();
            calls.add("<" + name);
            return outcome;
        };
    }

    @Test
    void a_command_is_routed_to_its_handler_and_its_result_is_returned_typed() {
        var bus = CommandBus.builder().handle(CreateCustomer.class, command -> ADA).build();

        Customer result = bus.execute(CREATE);

        assertSame(ADA, result);
    }

    @Test
    void middleware_wraps_the_handler_in_declaration_order_like_an_onion() {
        var calls = new ArrayList<String>();
        var bus = CommandBus.builder()
                .use(recording("outer", calls))
                .use(recording("inner", calls))
                .handle(CreateCustomer.class, command -> {
                    calls.add("handler");
                    return ADA;
                })
                .build();

        bus.execute(CREATE);

        assertEquals(List.of("outer>", "inner>", "handler", "<inner", "<outer"), calls);
    }

    @Test
    void middleware_sees_the_command_and_its_context() {
        var seen = new ArrayList<Object>();
        var bus = CommandBus.builder()
                .use((command, context, next) -> {
                    seen.add(command);
                    seen.add(context.idempotencyKey().orElseThrow());
                    return next.proceed();
                })
                .handle(CreateCustomer.class, command -> ADA)
                .build();

        bus.dispatch(CREATE, CommandContext.withIdempotencyKey("k-1"));

        assertEquals(List.of(CREATE, "k-1"), seen);
    }

    @Test
    void a_middleware_can_short_circuit_and_flag_a_replayed_outcome() {
        var bus = CommandBus.builder()
                .use((command, context, next) -> Outcome.replayed(ADA))
                .handle(CreateCustomer.class, command -> {
                    throw new AssertionError("must not run");
                })
                .build();

        var outcome = bus.dispatch(CREATE, CommandContext.NONE);

        assertTrue(outcome.replayed());
        assertSame(ADA, outcome.value());
        assertFalse(Outcome.fresh(ADA).replayed());
    }

    @Test
    void dispatching_a_command_without_a_handler_is_a_wiring_bug() {
        var bus = CommandBus.builder().build();

        var e = assertThrows(IllegalStateException.class,
                () -> bus.execute(new OpenAccount(TaxNumber.of("1"))));

        assertEquals("No handler registered for OpenAccount", e.getMessage());
    }

    @Test
    void registering_two_handlers_for_one_command_is_refused() {
        var builder = CommandBus.builder().handle(CreateCustomer.class, command -> ADA);

        assertThrows(IllegalStateException.class, () -> builder.handle(CreateCustomer.class, command -> ADA));
    }

    @Test
    void the_empty_context_has_no_idempotency_key_and_blank_keys_are_ignored() {
        assertTrue(CommandContext.NONE.idempotencyKey().isEmpty());
        assertTrue(CommandContext.withIdempotencyKey("  ").idempotencyKey().isEmpty());
        assertTrue(CommandContext.withIdempotencyKey(null).idempotencyKey().isEmpty());
    }
}
