package br.com.tiagotds.transfereasy.support;

import static br.com.tiagotds.transfereasy.jooq.Tables.ACCOUNTS;
import static br.com.tiagotds.transfereasy.jooq.Tables.LEDGER_ENTRIES;

import br.com.tiagotds.transfereasy.Core;
import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.application.command.DepositMoney;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.Statement;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import br.com.tiagotds.transfereasy.infrastructure.config.Config;
import br.com.tiagotds.transfereasy.infrastructure.config.Settings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.Database;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * A fully wired, isolated core on its own in-memory database, one per test. The helpers take raw strings like the
 * HTTP edge does, so scenario tests read like API calls.
 */
public final class TestEnvironment implements AutoCloseable {

    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    public final Database database;
    public final TransactionRunner tx;
    public final Core core;

    public TestEnvironment(Config config) {
        var settings = Settings.from(config);
        this.database = Database.startInMemory("test-" + UUID.randomUUID(), settings.database());
        this.tx = new TransactionRunner(database);
        this.core = Core.wire(settings, tx, FIXED_CLOCK, UUID::randomUUID);
    }

    public TestEnvironment(int poolSize) {
        this(Config.defaults().with("db.pool-size", String.valueOf(poolSize)));
    }

    public TestEnvironment() {
        this(16);
    }

    // ---- commands

    public Customer createCustomer(String taxNumber, String name) {
        return core.commands().execute(new CreateCustomer(TaxNumber.of(taxNumber), CustomerName.of(name)));
    }

    public Account open(String taxNumber) {
        return core.commands().execute(new OpenAccount(TaxNumber.of(taxNumber)));
    }

    public Account deposit(String number, BigDecimal amount) {
        return core.commands().execute(new DepositMoney(AccountNumber.of(number), amount));
    }

    public Account withdraw(String number, BigDecimal amount) {
        return core.commands().execute(new WithdrawMoney(AccountNumber.of(number), amount));
    }

    public TransferReceipt transfer(String from, String to, BigDecimal amount) {
        return core.commands().execute(new TransferMoney(AccountNumber.of(from),
                AccountNumber.of(to, "toAccountNumber"), amount));
    }

    // ---- queries

    public Account account(String number) {
        return core.accounts().get(AccountNumber.of(number));
    }

    public Statement statement(String number, Integer limit) {
        return core.accounts().statement(AccountNumber.of(number), limit);
    }

    public Customer customer(String taxNumber) {
        return core.customers().get(TaxNumber.of(taxNumber));
    }

    public List<Customer> search(String fragment) {
        return core.customers().search(fragment);
    }

    public List<Account> accountsOf(String taxNumber) {
        return core.customers().accountsOf(TaxNumber.of(taxNumber)).accounts();
    }

    // ---- fixtures and invariants

    /** Creates a customer with one account holding {@code balance}, and returns the account number. */
    public String accountWithBalance(String taxNumber, String balance) {
        createCustomer(taxNumber, "Customer " + taxNumber);
        var number = open(taxNumber).number().value();
        if (new BigDecimal(balance).signum() > 0) {
            deposit(number, new BigDecimal(balance));
        }
        return number;
    }

    public BigDecimal balanceOf(String accountNumber) {
        return account(accountNumber).balance().value();
    }

    public BigDecimal totalOfAllBalances() {
        return sum(ACCOUNTS.BALANCE);
    }

    /** Sum of every ledger line: must always equal {@link #totalOfAllBalances()} (double-entry invariant). */
    public BigDecimal totalOfAllLedgerEntries() {
        return sum(LEDGER_ENTRIES.AMOUNT);
    }

    private BigDecimal sum(Field<BigDecimal> column) {
        return tx.inReadOnlyTransaction(() -> TransactionRunner.current()
                .select(DSL.coalesce(DSL.sum(column), BigDecimal.ZERO))
                .from(column.getQualifiedName().first())
                .fetchSingle().value1());
    }

    @Override
    public void close() {
        database.close();
    }
}
