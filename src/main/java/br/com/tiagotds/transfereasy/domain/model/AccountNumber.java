package br.com.tiagotds.transfereasy.domain.model;

import java.util.UUID;

/** Public account identifier. Width matches {@code accounts.account_number VARCHAR(32)}. */
public record AccountNumber(String value) {

    public static final int MAX_LENGTH = 32;
    private static final String FIELD = "accountNumber";

    public AccountNumber {
        value = Text.require(value, FIELD, MAX_LENGTH);
    }

    public static AccountNumber of(String value) {
        return new AccountNumber(value);
    }

    /** Validates naming the request field, e.g. {@code toAccountNumber}. */
    public static AccountNumber of(String value, String field) {
        return new AccountNumber(Text.require(value, field, MAX_LENGTH));
    }

    /** 32 hex characters: a UUID without dashes. */
    public static AccountNumber generate(UUID uuid) {
        return new AccountNumber(uuid.toString().replace("-", ""));
    }

    @Override
    public String toString() {
        return value;
    }
}
