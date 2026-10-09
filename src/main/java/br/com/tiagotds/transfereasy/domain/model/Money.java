package br.com.tiagotds.transfereasy.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A non-negative amount of the single supported currency, always with exactly two decimal places so that
 * {@code 10} and {@code 10.00} are equal. The scale matches the {@code NUMERIC(19,2)} columns.
 */
public record Money(BigDecimal value) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + value);
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Money cannot have more than " + SCALE + " decimals: " + value);
        }
        value = value.setScale(SCALE);
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money of(String value) {
        return new Money(new BigDecimal(value));
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }

    /** @throws IllegalArgumentException if the result would be negative; callers check funds first */
    public Money minus(Money other) {
        return new Money(value.subtract(other.value));
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    /** Signed view used by the ledger, where debits are negative. */
    public BigDecimal negated() {
        return value.negate();
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
