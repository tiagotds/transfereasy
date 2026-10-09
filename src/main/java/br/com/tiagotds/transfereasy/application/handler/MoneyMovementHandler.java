package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.application.command.CommandHandler;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared steps of every handler that moves money: validate the amount, load the accounts involved, let the
 * aggregate decide, then persist each resulting {@link Movement} (new balance + its ledger posting) together.
 */
abstract class MoneyMovementHandler<C extends Command<R>, R> implements CommandHandler<C, R> {

    private final MovementDependencies deps;

    MoneyMovementHandler(MovementDependencies deps) {
        this.deps = deps;
    }

    protected Money validAmount(BigDecimal raw) {
        return deps.amounts().requireValid(raw);
    }

    /** The existing accounts among {@code numbers}, loaded as the configured {@link LockingStrategy} says. */
    protected List<Account> load(Collection<AccountNumber> numbers) {
        return deps.locking().load(deps.accounts(), numbers);
    }

    protected static Account pick(List<Account> loaded, AccountNumber number, Supplier<NotFound> missing) {
        return loaded.stream().filter(a -> a.number().equals(number)).findFirst().orElseThrow(missing);
    }

    protected Account record(Movement movement) {
        deps.accounts().save(movement.account());
        deps.ledger().append(movement.posting());
        return movement.account();
    }

    protected OffsetDateTime now() {
        return OffsetDateTime.now(deps.clock());
    }
}
