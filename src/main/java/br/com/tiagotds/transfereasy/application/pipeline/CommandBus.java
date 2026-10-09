package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.application.command.CommandHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes each command to its handler through the middleware chain. Middleware registered first is outermost, so
 * {@code use(retry).use(transaction)} retries whole transactions.
 */
public final class CommandBus {

    private final List<Middleware> middleware;
    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    private CommandBus(List<Middleware> middleware, Map<Class<?>, CommandHandler<?, ?>> handlers) {
        this.middleware = List.copyOf(middleware);
        this.handlers = Map.copyOf(handlers);
    }

    public static Builder builder() {
        return new Builder();
    }

    public <R> R execute(Command<R> command) {
        return dispatch(command, CommandContext.NONE).value();
    }

    @SuppressWarnings("unchecked") // handlers are registered per command class, and Command<R> fixes R
    public <R> Outcome<R> dispatch(Command<R> command, CommandContext context) {
        var handler = (CommandHandler<Command<R>, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + command.getClass().getSimpleName());
        }
        return (Outcome<R>) chain(0, command, context, () -> Outcome.fresh(handler.handle(command)));
    }

    private Outcome<?> chain(int index, Command<?> command, CommandContext context, Middleware.Next last) {
        if (index == middleware.size()) {
            return last.proceed();
        }
        return middleware.get(index).around(command, context, () -> chain(index + 1, command, context, last));
    }

    public static final class Builder {

        private final List<Middleware> middleware = new ArrayList<>();
        private final Map<Class<?>, CommandHandler<?, ?>> handlers = new HashMap<>();

        public Builder use(Middleware link) {
            middleware.add(link);
            return this;
        }

        public <R, C extends Command<R>> Builder handle(Class<C> type, CommandHandler<C, R> handler) {
            if (handlers.putIfAbsent(type, handler) != null) {
                throw new IllegalStateException("Duplicate handler for " + type.getSimpleName());
            }
            return this;
        }

        public CommandBus build() {
            return new CommandBus(middleware, handlers);
        }
    }
}
