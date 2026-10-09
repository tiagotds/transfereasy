package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.application.command.CommandHandler;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Shared steps of every handler that moves money: validate the amount, load the accounts involved, let the
 * aggregate decide, then persist each resulting {@link Movement} (new balance + its ledger posting) together.
 */
abstract class MoneyMovementHandler<C extends Command<R>, R> implements CommandHandler<C, R> {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final AmountPolicy amounts;
    private final Clock clock;

    MoneyMovementHandler(AccountRepository accounts, LedgerRepository ledger, AmountPolicy amounts, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.amounts = amounts;
        this.clock = clock;
    }

    protected Money validAmount(BigDecimal raw) {
        return amounts.requireValid(raw);
    }

    /** The existing accounts among {@code numbers}, ready to be changed, in ascending id order. */
    protected List<Account> load(Collection<AccountNumber> numbers) {
        return accounts.lockAll(numbers);
    }

    protected static Account pick(List<Account> loaded, AccountNumber number, java.util.function.Supplier<NotFound> missing) {
        return loaded.stream().filter(a -> a.number().equals(number)).findFirst().orElseThrow(missing);
    }

    protected Account record(Movement movement) {
        accounts.save(movement.account());
        ledger.append(movement.posting());
        return movement.account();
    }

    protected OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
