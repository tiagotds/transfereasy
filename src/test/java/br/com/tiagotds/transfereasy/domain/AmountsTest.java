package br.com.tiagotds.transfereasy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountsTest {

    @Test
    void valid_amounts_are_normalised_to_two_decimal_places() {
        assertEquals("10.00", Amounts.requirePositive(new BigDecimal("10")).toPlainString());
        assertEquals("0.01", Amounts.requirePositive(new BigDecimal("0.01")).toPlainString());
        assertEquals("5.10", Amounts.requirePositive(new BigDecimal("5.1000")).toPlainString());
        assertEquals("1000000000000.00", Amounts.requirePositive(Amounts.MAX).toPlainString());
        assertEquals("1000.00", Amounts.requirePositive(new BigDecimal("1E+3")).toPlainString());
    }

    @Test
    void invalid_amounts_are_rejected_with_a_meaningful_message() {
        assertEquals("Field 'amount' is required.", messageFor(null));
        assertEquals("Amount must be greater than zero.", messageFor(BigDecimal.ZERO));
        assertEquals("Amount must be greater than zero.", messageFor(new BigDecimal("-0.01")));
        assertEquals("Amount must have at most 2 decimal places.", messageFor(new BigDecimal("0.001")));
        assertEquals("Amount must not exceed 1000000000000.00.", messageFor(new BigDecimal("1000000000000.01")));
    }

    private static String messageFor(BigDecimal amount) {
        var e = assertThrows(DomainException.class, () -> Amounts.requirePositive(amount));
        assertEquals(DomainException.Code.INVALID, e.code());
        return e.getMessage();
    }
}
