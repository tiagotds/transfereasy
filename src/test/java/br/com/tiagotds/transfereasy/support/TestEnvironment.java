package br.com.tiagotds.transfereasy.support;

import br.com.tiagotds.transfereasy.db.Database;
import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import br.com.tiagotds.transfereasy.repository.LedgerRepository;
import br.com.tiagotds.transfereasy.service.AccountService;
import br.com.tiagotds.transfereasy.service.CustomerService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static br.com.tiagotds.transfereasy.jooq.Tables.ACCOUNTS;
import static br.com.tiagotds.transfereasy.jooq.Tables.LEDGER_ENTRIES;

/** A fully wired, isolated service stack on its own in-memory database. One per test. */
public final class TestEnvironment implements AutoCloseable {

    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    public final Database database;
    public final TransactionRunner tx;
    public final CustomerService customers;
    public final AccountService accounts;

    public TestEnvironment(int poolSize) {
        this.database = Database.startInMemory("test-" + UUID.randomUUID(), poolSize);
        this.tx = new TransactionRunner(database);
        var customerRepo = new CustomerRepository();
        var accountRepo = new AccountRepository();
        this.customers = new CustomerService(tx, customerRepo, accountRepo, FIXED_CLOCK);
        this.accounts = new AccountService(tx, customerRepo, accountRepo, new LedgerRepository(),
                FIXED_CLOCK, UUID::randomUUID);
    }

    public TestEnvironment() {
        this(16);
    }

    /** Creates a customer with one account holding {@code balance}, and returns the account number. */
    public String accountWithBalance(String taxNumber, String balance) {
        customers.create(taxNumber, "Customer " + taxNumber);
        var number = accounts.open(taxNumber).number();
        if (new BigDecimal(balance).signum() > 0) {
            accounts.deposit(number, new BigDecimal(balance));
        }
        return number;
    }

    public BigDecimal balanceOf(String accountNumber) {
        return accounts.get(accountNumber).balance();
    }

    public BigDecimal totalOfAllBalances() {
        return tx.inReadOnlyTransaction(db -> db.select(org.jooq.impl.DSL.coalesce(
                        org.jooq.impl.DSL.sum(ACCOUNTS.BALANCE), BigDecimal.ZERO)).from(ACCOUNTS).fetchSingle().value1());
    }

    /** Sum of every ledger line: must always equal {@link #totalOfAllBalances()} (double-entry invariant). */
    public BigDecimal totalOfAllLedgerEntries() {
        return tx.inReadOnlyTransaction(db -> db.select(org.jooq.impl.DSL.coalesce(
                        org.jooq.impl.DSL.sum(LEDGER_ENTRIES.AMOUNT), BigDecimal.ZERO)).from(LEDGER_ENTRIES)
                .fetchSingle().value1());
    }

    @Override
    public void close() {
        database.close();
    }
}
