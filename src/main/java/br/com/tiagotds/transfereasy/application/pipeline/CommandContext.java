package br.com.tiagotds.transfereasy.application.pipeline;

import java.util.Optional;

/** Request metadata that travels with a command but is not part of it. */
public record CommandContext(String key) {

    public static final CommandContext NONE = new CommandContext(null);

    /** A blank or missing key means "no idempotency requested". */
    public static CommandContext withIdempotencyKey(String key) {
        return key == null || key.isBlank() ? NONE : new CommandContext(key.trim());
    }

    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(key);
    }
}
