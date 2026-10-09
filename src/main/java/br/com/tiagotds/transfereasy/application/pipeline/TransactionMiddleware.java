package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;

/** Runs the rest of the chain, handler included, as one read-committed transaction. */
public final class TransactionMiddleware implements Middleware {

    private final TransactionRunner tx;

    public TransactionMiddleware(TransactionRunner tx) {
        this.tx = tx;
    }

    @Override
    public Outcome<?> around(Command<?> command, CommandContext context, Next next) {
        return tx.inTransaction(next::proceed);
    }
}
