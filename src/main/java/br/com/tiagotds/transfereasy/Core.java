package br.com.tiagotds.transfereasy;

import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.application.command.DepositMoney;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.application.handler.CreateCustomerHandler;
import br.com.tiagotds.transfereasy.application.handler.DepositHandler;
import br.com.tiagotds.transfereasy.application.handler.OpenAccountHandler;
import br.com.tiagotds.transfereasy.application.handler.TransferHandler;
import br.com.tiagotds.transfereasy.application.handler.WithdrawHandler;
import br.com.tiagotds.transfereasy.application.pipeline.CommandBus;
import br.com.tiagotds.transfereasy.application.pipeline.TransactionMiddleware;
import br.com.tiagotds.transfereasy.application.query.AccountQueries;
import br.com.tiagotds.transfereasy.application.query.CustomerQueries;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.JooqAccountRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.JooqCustomerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.JooqLedgerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/** Everything below the HTTP edge, wired by hand: the write side (command bus) and the read side (queries). */
public record Core(CommandBus commands, AccountQueries accounts, CustomerQueries customers) {

    public static Core wire(Settings settings, TransactionRunner tx, Clock clock, Supplier<UUID> ids) {
        var customerRepository = new JooqCustomerRepository();
        var accountRepository = new JooqAccountRepository();
        var ledger = new JooqLedgerRepository();
        var amounts = settings.money().amountPolicy();

        var commands = CommandBus.builder()
                .use(new TransactionMiddleware(tx))
                .handle(CreateCustomer.class, new CreateCustomerHandler(customerRepository, clock))
                .handle(OpenAccount.class, new OpenAccountHandler(customerRepository, accountRepository, clock, ids))
                .handle(DepositMoney.class, new DepositHandler(accountRepository, ledger, amounts, clock))
                .handle(WithdrawMoney.class, new WithdrawHandler(accountRepository, ledger, amounts, clock))
                .handle(TransferMoney.class, new TransferHandler(accountRepository, ledger, amounts, clock, ids))
                .build();
        return new Core(commands,
                new AccountQueries(tx, accountRepository, ledger, settings.statements()),
                new CustomerQueries(tx, customerRepository, accountRepository));
    }
}
