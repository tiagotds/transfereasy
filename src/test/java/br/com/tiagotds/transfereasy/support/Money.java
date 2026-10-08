package br.com.tiagotds.transfereasy.support;

import java.math.BigDecimal;

public final class Money {

    private Money() {
    }

    /** Scale-insensitive BigDecimal factory: {@code Money.of("10")} equals {@code Money.of("10.00")}. */
    public static BigDecimal of(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
