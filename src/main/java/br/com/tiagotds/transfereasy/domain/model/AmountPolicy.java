package br.com.tiagotds.transfereasy.domain.model;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.math.BigDecimal;

/**
 * Turns an untrusted amount into {@link Money} for a movement: positive, at most two decimals, at most
 * {@code max} (configured by {@code money.max-amount}).
 */
public record AmountPolicy(Money max) {

    public Money requireValid(BigDecimal amount) {
        if (amount == null) {
            throw InvalidInput.required("amount");
        }
        if (amount.signum() <= 0) {
            throw new InvalidInput("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > Money.SCALE) {
            throw new InvalidInput("Amount must have at most " + Money.SCALE + " decimal places.");
        }
        var money = Money.of(amount);
        if (max.isLessThan(money)) {
            throw new InvalidInput("Amount must not exceed " + max + ".");
        }
        return money;
    }
}
