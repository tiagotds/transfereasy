package br.com.tiagotds.transfereasy.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void money_is_always_normalised_to_two_decimal_places_so_equal_amounts_are_equal() {
        assertEquals(Money.of("10"), Money.of("10.00"));
        assertEquals(Money.of("5.10"), Money.of(new BigDecimal("5.1000")));
        assertEquals("1000.00", Money.of(new BigDecimal("1E+3")).value().toPlainString());
        assertEquals("0.00", Money.ZERO.value().toPlainString());
    }

    @Test
    void money_cannot_be_negative_or_finer_than_a_cent() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("-0.01"));
        assertThrows(IllegalArgumentException.class, () -> Money.of("0.001"));
        assertThrows(NullPointerException.class, () -> Money.of((BigDecimal) null));
    }

    @Test
    void arithmetic_and_comparison() {
        assertEquals(Money.of("12.50"), Money.of("10").plus(Money.of("2.50")));
        assertEquals(Money.of("7.50"), Money.of("10").minus(Money.of("2.50")));
        assertTrue(Money.of("1").isLessThan(Money.of("1.01")));
        assertFalse(Money.of("1").isLessThan(Money.of("1")));
        assertTrue(Money.of("2").compareTo(Money.of("1")) > 0);
    }

    @Test
    void subtracting_more_than_available_is_a_programming_error() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("1").minus(Money.of("1.01")));
    }

    @Test
    void the_signed_view_negates_debits() {
        assertEquals(new BigDecimal("-3.00"), Money.of("3").negated());
    }
}
