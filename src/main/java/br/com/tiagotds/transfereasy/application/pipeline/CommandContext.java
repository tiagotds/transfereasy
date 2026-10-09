package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.util.Optional;

/** Request metadata that travels with a command but is not part of it. */
public record CommandContext(String key) {

    public static final CommandContext NONE = new CommandContext(null);
    static final int MAX_KEY_LENGTH = 64;

    /** A blank or missing key means "no idempotency requested". */
    public static CommandContext withIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return NONE;
        }
        var trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new InvalidInput("Header 'Idempotency-Key' must have at most " + MAX_KEY_LENGTH + " characters.");
        }
        return new CommandContext(trimmed);
    }

    public Optional<String> idempotencyKey() {
        return Optional.ofNullable(key);
    }
}
