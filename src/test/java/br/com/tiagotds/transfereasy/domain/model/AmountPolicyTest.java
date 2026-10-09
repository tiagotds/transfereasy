package br.com.tiagotds.transfereasy.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountPolicyTest {

    private final AmountPolicy policy = new AmountPolicy(Money.of("1000"));

    @Test
    void valid_amounts_become_money() {
        assertEquals(Money.of("0.01"), policy.requireValid(new BigDecimal("0.01")));
        assertEquals(Money.of("1000"), policy.requireValid(new BigDecimal("1000.000")));
    }

    @Test
    void invalid_amounts_are_rejected_with_a_meaningful_message() {
        assertEquals("Field 'amount' is required.", messageFor(null));
        assertEquals("Amount must be greater than zero.", messageFor(BigDecimal.ZERO));
        assertEquals("Amount must be greater than zero.", messageFor(new BigDecimal("-0.01")));
        assertEquals("Amount must have at most 2 decimal places.", messageFor(new BigDecimal("0.001")));
        assertEquals("Amount must not exceed 1000.00.", messageFor(new BigDecimal("1000.01")));
    }

    private String messageFor(BigDecimal amount) {
        return assertThrows(InvalidInput.class, () -> policy.requireValid(amount)).getMessage();
    }
}
