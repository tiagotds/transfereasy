package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import java.math.BigDecimal;

public record TransferMoney(AccountNumber from, AccountNumber to, BigDecimal amount)
        implements Command<TransferReceipt> {

    public TransferMoney {
        if (from.equals(to)) {
            throw new InvalidInput("Origin and destination accounts must be different.");
        }
        amount = Amounts.canonical(amount);
    }
}
