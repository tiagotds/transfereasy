package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.domain.Account;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.EntryKind;
import br.com.tiagotds.transfereasy.domain.Statement;
import br.com.tiagotds.transfereasy.domain.TransferReceipt;
import br.com.tiagotds.transfereasy.infrastructure.config.StatementSettings;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import br.com.tiagotds.transfereasy.repository.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.jooq.DSLContext;

/**
 * Money movements. Every public method runs inside exactly one database transaction, opened and closed by
 * {@link TransactionRunner}, and every balance check happens <em>inside</em> that transaction, either as part
 * of an atomic conditional UPDATE or while holding row locks.
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
        return tx.inTransaction(db -> {
            var customer = customers.findByTaxNumber(db, taxNumber.value())
                    .orElseThrow(NotFound::customer);
            return accounts.insert(db, AccountNumber.generate(ids.get()).value(), customer.id(), now());
        });
    }

    public Account get(String number) {
        return tx.inReadOnlyTransaction(db -> accounts.findByNumber(db, number)
                .orElseThrow(() -> accountNotFound(number)));
    }

    public Statement statement(String number, Integer requestedSize) {
        int size = requestedSize == null ? statements.defaultSize() : requestedSize;
        if (size < 1 || size > statements.maxSize()) {
            throw new InvalidInput("Parameter 'limit' must be between 1 and " + statements.maxSize() + ".");
        }
        return tx.inReadOnlyTransaction(db -> {
            var account = accounts.findByNumber(db, number).orElseThrow(() -> accountNotFound(number));
            return new Statement(account, ledger.findLatest(db, account.id(), size));
        });
    }

    public Account deposit(String number, BigDecimal rawAmount) {
        var amount = amounts.requireValid(rawAmount).value();
        return tx.inTransaction(db -> {
            var account = accounts.findByNumber(db, number).orElseThrow(() -> accountNotFound(number));
            var updated = accounts.credit(db, account.id(), amount);
            ledger.append(db, account.id(), EntryKind.DEPOSIT, amount, updated.balance(), null, null, now());
            return updated;
        });
    }

    public Account withdraw(String number, BigDecimal rawAmount) {
        var amount = amounts.requireValid(rawAmount).value();
        return tx.inTransaction(db -> {
            var account = accounts.findByNumber(db, number).orElseThrow(() -> accountNotFound(number));
            var updated = accounts.debitIfSufficient(db, account.id(), amount)
                    .orElseThrow(AccountService::insufficientFunds);
            ledger.append(db, account.id(), EntryKind.WITHDRAWAL, amount.negate(), updated.balance(),
                    null, null, now());
            return updated;
        });
    }

    public TransferReceipt transfer(String fromNumber, String toNumber, BigDecimal rawAmount) {
        var destination = AccountNumber.of(toNumber, "toAccountNumber").value();
        if (destination.equals(fromNumber)) {
            throw new InvalidInput("Origin and destination accounts must be different.");
        }
        var amount = amounts.requireValid(rawAmount).value();
        var transferId = ids.get().toString();
        return tx.inTransaction(db -> {
            var locked = accounts.lockByNumbers(db, List.of(fromNumber, destination));
            var from = find(locked, fromNumber, () -> accountNotFound(fromNumber));
            var to = find(locked, destination, NotFound::destinationAccount);
            return move(db, from, to, amount, transferId);
        });
    }

    private TransferReceipt move(DSLContext db, Account from, Account to, BigDecimal amount, String transferId) {
        var debited = accounts.debitIfSufficient(db, from.id(), amount).orElseThrow(AccountService::insufficientFunds);
        var credited = accounts.credit(db, to.id(), amount);
        var at = now();
        ledger.append(db, from.id(), EntryKind.TRANSFER_OUT, amount.negate(), debited.balance(),
                to.number(), transferId, at);
        ledger.append(db, to.id(), EntryKind.TRANSFER_IN, amount, credited.balance(), from.number(), transferId, at);
        return new TransferReceipt(transferId, debited, credited);
    }

    private static Account find(List<Account> accounts, String number, Supplier<NotFound> missing) {
        return accounts.stream().filter(a -> a.number().equals(number)).findFirst().orElseThrow(missing);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static NotFound accountNotFound(String number) {
        return NotFound.account(AccountNumber.of(number));
    }

    private static InsufficientFunds insufficientFunds() {
        return new InsufficientFunds();
    }
}
