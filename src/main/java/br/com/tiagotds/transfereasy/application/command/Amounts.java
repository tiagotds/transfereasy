package br.com.tiagotds.transfereasy.application.command;

import java.math.BigDecimal;

final class Amounts {

    private Amounts() {
    }

    /** {@code 10} and {@code 10.00} are the same request; validation of the value itself is left to AmountPolicy. */
    static BigDecimal canonical(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros();
    }
}
