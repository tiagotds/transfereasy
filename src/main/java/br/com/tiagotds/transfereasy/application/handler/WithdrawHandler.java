package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;

public final class WithdrawHandler extends SingleAccountMovementHandler<WithdrawMoney> {

    public WithdrawHandler(AccountRepository accounts, LedgerRepository ledger, AmountPolicy amounts, Clock clock) {
        super(accounts, ledger, amounts, clock);
    }

    @Override
    protected Movement move(Account account, Money amount, OffsetDateTime at) {
        return account.withdraw(amount, at);
    }
}
