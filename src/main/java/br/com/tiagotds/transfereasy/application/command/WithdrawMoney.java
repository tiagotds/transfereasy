package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import java.math.BigDecimal;

public record WithdrawMoney(AccountNumber account, BigDecimal amount) implements AccountMovement {

    public WithdrawMoney {
        amount = Amounts.canonical(amount);
    }
}
