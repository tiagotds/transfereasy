package br.com.tiagotds.transfereasy.repository;

import static br.com.tiagotds.transfereasy.jooq.Tables.ACCOUNTS;

import br.com.tiagotds.transfereasy.domain.Account;
import br.com.tiagotds.transfereasy.jooq.tables.records.AccountsRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;

public final class AccountRepository {

    public Account insert(DSLContext tx, String number, long customerId, OffsetDateTime now) {
        return toDomain(tx.insertInto(ACCOUNTS)
                .set(ACCOUNTS.ACCOUNT_NUMBER, number)
                .set(ACCOUNTS.CUSTOMER_ID, customerId)
                .set(ACCOUNTS.BALANCE, BigDecimal.ZERO.setScale(2))
                .set(ACCOUNTS.CREATED_AT, now)
                .returning()
                .fetchSingle());
    }

    public Optional<Account> findByNumber(DSLContext tx, String number) {
        return tx.selectFrom(ACCOUNTS).where(ACCOUNTS.ACCOUNT_NUMBER.eq(number))
                .fetchOptional().map(AccountRepository::toDomain);
    }

    public List<Account> findByCustomer(DSLContext tx, long customerId) {
        return tx.selectFrom(ACCOUNTS).where(ACCOUNTS.CUSTOMER_ID.eq(customerId))
                .orderBy(ACCOUNTS.ID).fetch(AccountRepository::toDomain);
    }

    /**
     * Row-locks the given accounts (SELECT ... FOR UPDATE) in ascending id order and returns them in that order.
     * A single, global lock order is what makes opposite concurrent transfers (A to B and B to A) deadlock-free.
     */
    public List<Account> lockByNumbers(DSLContext tx, List<String> numbers) {
        return tx.selectFrom(ACCOUNTS).where(ACCOUNTS.ACCOUNT_NUMBER.in(numbers))
                .orderBy(ACCOUNTS.ID).forUpdate().fetch(AccountRepository::toDomain);
    }

    /** Atomic credit; the row lock taken by the UPDATE is held until the transaction ends. */
    public Account credit(DSLContext tx, long accountId, BigDecimal amount) {
        return toDomain(tx.update(ACCOUNTS)
                .set(ACCOUNTS.BALANCE, ACCOUNTS.BALANCE.plus(amount))
                .where(ACCOUNTS.ID.eq(accountId))
                .returning().fetchSingle());
    }

    /**
     * Atomic, conditional debit: the balance check and the decrement are a single statement,
     * so two concurrent withdrawals can never both succeed on funds that only cover one.
     *
     * @return the updated account, or empty when the funds are insufficient
     */
    public Optional<Account> debitIfSufficient(DSLContext tx, long accountId, BigDecimal amount) {
        return tx.update(ACCOUNTS)
                .set(ACCOUNTS.BALANCE, ACCOUNTS.BALANCE.minus(amount))
                .where(ACCOUNTS.ID.eq(accountId).and(ACCOUNTS.BALANCE.ge(amount)))
                .returning().fetchOptional().map(AccountRepository::toDomain);
    }

    private static Account toDomain(AccountsRecord r) {
        return new Account(r.getId(), r.getAccountNumber(), r.getCustomerId(), r.getBalance(), r.getCreatedAt());
    }
}
