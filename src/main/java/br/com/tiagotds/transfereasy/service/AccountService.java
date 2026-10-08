package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.domain.Account;
import br.com.tiagotds.transfereasy.domain.Amounts;
import br.com.tiagotds.transfereasy.domain.DomainException;
import br.com.tiagotds.transfereasy.domain.EntryKind;
import br.com.tiagotds.transfereasy.domain.Statement;
import br.com.tiagotds.transfereasy.domain.TransferReceipt;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import br.com.tiagotds.transfereasy.repository.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jooq.DSLContext;

/**
 * Money movements. Every public method runs inside exactly one database transaction, opened and closed by
 * {@link TransactionRunner}, and every balance check happens <em>inside</em> that transaction, either as part
 * of an atomic conditional UPDATE or while holding row locks.
 */
public final class AccountService {

    public static final int DEFAULT_STATEMENT_SIZE = 100;
    public static final int MAX_STATEMENT_SIZE = 1000;

    private final TransactionRunner tx;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public AccountService(TransactionRunner tx, CustomerRepository customers, AccountRepository accounts,
                          LedgerRepository ledger, Clock clock, Supplier<UUID> ids) {
        this.tx = tx;
        this.customers = customers;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
        this.ids = ids;
    }

    public Account open(String customerTaxNumber) {
        if (customerTaxNumber == null || customerTaxNumber.isBlank()) {
            throw DomainException.invalid("Field 'taxNumber' is required.");
        }
        return tx.inTransaction(db -> {
            var customer = customers.findByTaxNumber(db, customerTaxNumber.trim())
                    .orElseThrow(() -> DomainException.notFound("Customer not found."));
            return accounts.insert(db, ids.get().toString().replace("-", ""), customer.id(), now());
        });
    }

    public Account get(String number) {
        return tx.inReadOnlyTransaction(db -> accounts.findByNumber(db, number)
                .orElseThrow(() -> accountNotFound(number)));
    }

    public Statement statement(String number, Integer requestedSize) {
        int size = requestedSize == null ? DEFAULT_STATEMENT_SIZE : requestedSize;
        if (size < 1 || size > MAX_STATEMENT_SIZE) {
            throw DomainException.invalid("Parameter 'limit' must be between 1 and " + MAX_STATEMENT_SIZE + ".");
        }
        return tx.inReadOnlyTransaction(db -> {
            var account = accounts.findByNumber(db, number).orElseThrow(() -> accountNotFound(number));
            return new Statement(account, ledger.findLatest(db, account.id(), size));
        });
    }

    public Account deposit(String number, BigDecimal rawAmount) {
        var amount = Amounts.requirePositive(rawAmount);
        return tx.inTransaction(db -> {
            var account = accounts.findByNumber(db, number).orElseThrow(() -> accountNotFound(number));
            var updated = accounts.credit(db, account.id(), amount);
            ledger.append(db, account.id(), EntryKind.DEPOSIT, amount, updated.balance(), null, null, now());
            return updated;
        });
    }

    public Account withdraw(String number, BigDecimal rawAmount) {
        var amount = Amounts.requirePositive(rawAmount);
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
        if (toNumber == null || toNumber.isBlank()) {
            throw DomainException.invalid("Field 'toAccountNumber' is required.");
        }
        if (Objects.equals(fromNumber, toNumber)) {
            throw DomainException.invalid("Origin and destination accounts must be different.");
        }
        var amount = Amounts.requirePositive(rawAmount);
        var transferId = ids.get().toString();
        return tx.inTransaction(db -> {
            var locked = accounts.lockByNumbers(db, List.of(fromNumber, toNumber));
            var from = find(locked, fromNumber, () -> accountNotFound(fromNumber));
            var to = find(locked, toNumber, () -> DomainException.notFound("Destination account not found."));
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

    private static Account find(List<Account> accounts, String number, Supplier<DomainException> missing) {
        return accounts.stream().filter(a -> a.number().equals(number)).findFirst().orElseThrow(missing);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static DomainException accountNotFound(String number) {
        return DomainException.notFound("Account '" + number + "' not found.");
    }

    private static DomainException insufficientFunds() {
        return DomainException.insufficientFunds("Insufficient funds for this operation.");
    }
}
