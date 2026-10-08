package br.com.tiagotds.transfereasy.domain;

import java.math.BigDecimal;

/** Validation and normalisation of monetary amounts (always exact, never floating point). */
public final class Amounts {

    public static final int SCALE = 2;
    public static final BigDecimal MAX = new BigDecimal("1000000000000.00");
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Amounts() {
    }

    /** @return the amount with scale 2, or throws {@link DomainException} when it is not a valid movement amount. */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw DomainException.invalid("Field 'amount' is required.");
        }
        if (amount.signum() <= 0) {
            throw DomainException.invalid("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw DomainException.invalid("Amount must have at most " + SCALE + " decimal places.");
        }
        if (amount.compareTo(MAX) > 0) {
            throw DomainException.invalid("Amount must not exceed " + MAX.toPlainString() + ".");
        }
        return amount.setScale(SCALE);
    }
}
