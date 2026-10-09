package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.CommandHandler;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

public final class OpenAccountHandler implements CommandHandler<OpenAccount, Account> {

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public OpenAccountHandler(CustomerRepository customers, AccountRepository accounts, Clock clock,
                              Supplier<UUID> ids) {
        this.customers = customers;
        this.accounts = accounts;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Account handle(OpenAccount command) {
        var customer = customers.find(command.customer()).orElseThrow(NotFound::customer);
        return accounts.add(Account.open(AccountNumber.generate(ids.get()), customer.id(), OffsetDateTime.now(clock)));
    }
}
