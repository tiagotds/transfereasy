package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;

/**
 * One link of the command pipeline (Chain of Responsibility). It may act before and after {@link Next#proceed()},
 * call it several times (retry), or not at all (replay).
 */
@FunctionalInterface
public interface Middleware {

    Outcome<?> around(Command<?> command, CommandContext context, Next next);

    @FunctionalInterface
    interface Next {
        Outcome<?> proceed();
    }
}
