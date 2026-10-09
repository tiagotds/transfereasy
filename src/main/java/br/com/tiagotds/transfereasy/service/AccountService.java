package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.model.Movement;
import br.com.tiagotds.transfereasy.domain.model.Statement;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.model.Transfer;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import br.com.tiagotds.transfereasy.infrastructure.config.StatementSettings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Money movements. Every public method runs inside exactly one transaction; the aggregate decides, the repository
 * persists with a version compare-and-set, and accounts are row-locked first so concurrent writers queue up.
 */
public final class AccountService {

    private final TransactionRunner tx;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final Clock clock;
    private final Supplier<UUID> ids;
    private final StatementSettings statements;
    private final AmountPolicy amounts;

    public AccountService(TransactionRunner tx, CustomerRepository customers, AccountRepository accounts,
                          LedgerRepository ledger, Clock clock, Supplier<UUID> ids,
                          StatementSettings statements, AmountPolicy amounts) {
        this.tx = tx;
        this.customers = customers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
        this.ids = ids;
        this.statements = statements;
        this.amounts = amounts;
    }

    public Account open(String customerTaxNumber) {
        var taxNumber = TaxNumber.of(customerTaxNumber);
        return tx.inTransaction(() -> {
            var customer = customers.find(taxNumber).orElseThrow(NotFound::customer);
            return accounts.add(Account.open(AccountNumber.generate(ids.get()), customer.id(), now()));
        });
    }

    public Account get(String number) {
        var accountNumber = AccountNumber.of(number);
        return tx.inReadOnlyTransaction(() -> accounts.find(accountNumber)
                .orElseThrow(() -> NotFound.account(accountNumber)));
    }

    public Statement statement(String number, Integer requestedSize) {
        int size = requestedSize == null ? statements.defaultSize() : requestedSize;
        if (size < 1 || size > statements.maxSize()) {
            throw new InvalidInput("Parameter 'limit' must be between 1 and " + statements.maxSize() + ".");
        }
        var accountNumber = AccountNumber.of(number);
        return tx.inReadOnlyTransaction(() -> {
            var account = accounts.find(accountNumber).orElseThrow(() -> NotFound.account(accountNumber));
            return new Statement(account, ledger.latest(account.id(), size));
        });
    }

    public Account deposit(String number, BigDecimal rawAmount) {
        var amount = amounts.requireValid(rawAmount);
        var accountNumber = AccountNumber.of(number);
        return tx.inTransaction(() -> record(lock(accountNumber).deposit(amount, now())));
    }

    public Account withdraw(String number, BigDecimal rawAmount) {
        var amount = amounts.requireValid(rawAmount);
        var accountNumber = AccountNumber.of(number);
        return tx.inTransaction(() -> record(lock(accountNumber).withdraw(amount, now())));
    }

    public TransferReceipt transfer(String fromNumber, String toNumber, BigDecimal rawAmount) {
        var to = AccountNumber.of(toNumber, "toAccountNumber");
        var from = AccountNumber.of(fromNumber);
        if (from.equals(to)) {
            throw new InvalidInput("Origin and destination accounts must be different.");
        }
        var amount = amounts.requireValid(rawAmount);
        var transferId = ids.get().toString();
        return tx.inTransaction(() -> {
            var locked = accounts.lockAll(List.of(from, to));
            var origin = pick(locked, from, () -> NotFound.account(from));
            var destination = pick(locked, to, NotFound::destinationAccount);
            var transfer = Transfer.between(origin, destination, amount, transferId, now());
            return new TransferReceipt(transfer.id(), record(transfer.debit()), record(transfer.credit()));
        });
    }

    private Account lock(AccountNumber number) {
        return accounts.lockAll(List.of(number)).stream().findFirst().orElseThrow(() -> NotFound.account(number));
    }

    private Account record(Movement movement) {
        accounts.save(movement.account());
        ledger.append(movement.posting());
        return movement.account();
    }

    private static Account pick(List<Account> accounts, AccountNumber number, Supplier<NotFound> missing) {
        return accounts.stream().filter(a -> a.number().equals(number)).findFirst().orElseThrow(missing);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
