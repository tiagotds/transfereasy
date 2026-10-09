package br.com.tiagotds.transfereasy.domain.model;

/** A customer's natural, unique identifier. Width matches {@code customers.tax_number VARCHAR(32)}. */
public record TaxNumber(String value) {

    public static final int MAX_LENGTH = 32;

    public TaxNumber {
        value = Text.require(value, "taxNumber", MAX_LENGTH);
    }

    public static TaxNumber of(String value) {
        return new TaxNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
