package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import java.time.OffsetDateTime;

public final class WithdrawHandler extends SingleAccountMovementHandler<WithdrawMoney> {

    public WithdrawHandler(MovementDependencies deps) {
        super(deps);
    }

    @Override
    protected Movement move(Account account, Money amount, OffsetDateTime at) {
        return account.withdraw(amount, at);
    }
}
