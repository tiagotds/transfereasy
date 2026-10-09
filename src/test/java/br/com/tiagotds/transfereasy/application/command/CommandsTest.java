package br.com.tiagotds.transfereasy.application.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CommandsTest {

    @Test
    void a_transfer_command_refuses_the_same_account_on_both_sides() {
        var a = AccountNumber.of("A");

        var e = assertThrows(InvalidInput.class, () -> new TransferMoney(a, a, BigDecimal.ONE));

        assertEquals("Origin and destination accounts must be different.", e.getMessage());
    }

    @Test
    void equal_amounts_written_differently_describe_the_same_command() {
        var a = AccountNumber.of("A");

        assertEquals(new DepositMoney(a, new BigDecimal("10")), new DepositMoney(a, new BigDecimal("10.00")));
        assertEquals(new WithdrawMoney(a, new BigDecimal("1.5")).toString(),
                new WithdrawMoney(a, new BigDecimal("1.50")).toString());
    }

    @Test
    void a_missing_amount_is_kept_for_the_amount_policy_to_report() {
        assertNull(new DepositMoney(AccountNumber.of("A"), null).amount());
    }
}
