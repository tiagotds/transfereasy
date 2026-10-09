package br.com.tiagotds.transfereasy.application.command;

/** Executes one kind of command. Runs inside whatever the middleware chain set up (transaction, retry...). */
@FunctionalInterface
public interface CommandHandler<C extends Command<R>, R> {

    R handle(C command);
}
