package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import java.math.BigDecimal;

/** A command that changes the balance of exactly one account. */
public sealed interface AccountMovement extends Command<Account> permits DepositMoney, WithdrawMoney {

    AccountNumber account();

    BigDecimal amount();
}
