package br.com.tiagotds.transfereasy.domain.model;

/** Width matches {@code customers.name VARCHAR(120)}. */
public record CustomerName(String value) {

    public static final int MAX_LENGTH = 120;

    public CustomerName {
        value = Text.require(value, "name", MAX_LENGTH);
    }

    public static CustomerName of(String value) {
        return new CustomerName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
