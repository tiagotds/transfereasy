package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.AccountMovement;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Template Method for deposits and withdrawals: the algorithm is fixed here, subclasses only say which movement
 * the account performs.
 */
abstract class SingleAccountMovementHandler<C extends AccountMovement> extends MoneyMovementHandler<C, Account> {

    SingleAccountMovementHandler(MovementDependencies deps) {
        super(deps);
    }

    protected abstract Movement move(Account account, Money amount, OffsetDateTime at);

    @Override
    public final Account handle(C command) {
        var amount = validAmount(command.amount());
        var account = pick(load(List.of(command.account())), command.account(),
                () -> NotFound.account(command.account()));
        return record(move(account, amount, now()));
    }
}
