package br.com.tiagotds.transfereasy.domain.model;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;

/** Shared validation for the textual value objects: required, trimmed, bounded by the column width. */
final class Text {

    private Text() {
    }

    static String require(String raw, String field, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw InvalidInput.required(field);
        }
        var trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidInput("Field '" + field + "' must have at most " + maxLength + " characters.");
        }
        return trimmed;
    }
}
