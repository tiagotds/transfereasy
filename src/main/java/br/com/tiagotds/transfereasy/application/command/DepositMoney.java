package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import java.math.BigDecimal;

public record DepositMoney(AccountNumber account, BigDecimal amount) implements AccountMovement {

    public DepositMoney {
        amount = Amounts.canonical(amount);
    }
}
