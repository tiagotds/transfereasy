package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.domain.error.IdempotencyKeyReused;
import br.com.tiagotds.transfereasy.domain.port.IdempotencyStore;

/**
 * Exactly-once execution for requests carrying an Idempotency-Key. Must run <em>inside</em>
 * {@link TransactionMiddleware} (and therefore inside {@link RetryMiddleware}):
 * <ol>
 *   <li>a committed result for the key exists: replay it if the request is the same, refuse it otherwise;</li>
 *   <li>otherwise claim the key, run the command, and store its result, all in the operation's transaction.</li>
 * </ol>
 * Concurrent duplicates collide on the claim (the key's primary key) before doing any work; the loser is retried,
 * finds the winner's committed result at step 1, and replays it. A failed operation rolls its claim back, so the
 * client may retry with the same key.
 */
public final class IdempotencyMiddleware implements Middleware {

    private final IdempotencyStore store;
    private final ResultCodec codec;

    public IdempotencyMiddleware(IdempotencyStore store, ResultCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    @Override
    public Outcome<?> around(Command<?> command, CommandContext context, Next next) {
        var key = context.idempotencyKey();
        if (key.isEmpty()) {
            return next.proceed();
        }
        var fingerprint = codec.fingerprint(command);
        var previous = store.find(key.get());
        if (previous.isPresent()) {
            if (!previous.get().fingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyReused(key.get());
            }
            return Outcome.replayed(codec.decode(previous.get()));
        }
        store.claim(key.get(), fingerprint);
        var outcome = next.proceed();
        store.complete(key.get(), codec.encode(fingerprint, outcome.value()));
        return outcome;
    }
}
