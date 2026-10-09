package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static br.com.tiagotds.transfereasy.jooq.Tables.ACCOUNTS;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.jooq.tables.records.AccountsRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;

public final class JooqAccountRepository extends JooqRepository<AccountsRecord, Account>
        implements AccountRepository {

    public JooqAccountRepository() {
        super(ACCOUNTS);
    }

    @Override
    public Account add(Account newAccount) {
        return insert(Map.of(
                ACCOUNTS.ACCOUNT_NUMBER, newAccount.number().value(),
                ACCOUNTS.CUSTOMER_ID, newAccount.customerId(),
                ACCOUNTS.BALANCE, newAccount.balance().value(),
                ACCOUNTS.VERSION, newAccount.version(),
                ACCOUNTS.CREATED_AT, newAccount.createdAt()));
    }

    @Override
    public Optional<Account> find(AccountNumber number) {
        return findOne(ACCOUNTS.ACCOUNT_NUMBER.eq(number.value()));
    }

    @Override
    public List<Account> findByCustomer(long customerId) {
        return findAll(ACCOUNTS.CUSTOMER_ID.eq(customerId), ACCOUNTS.ID);
    }

    @Override
    public List<Account> findAll(Collection<AccountNumber> numbers) {
        return findAll(numberIn(numbers), ACCOUNTS.ID);
    }

    @Override
    public List<Account> lockAll(Collection<AccountNumber> numbers) {
        return db().selectFrom(ACCOUNTS).where(numberIn(numbers)).orderBy(ACCOUNTS.ID).forUpdate()
                .fetch(this::toDomain);
    }

    private static Condition numberIn(Collection<AccountNumber> numbers) {
        return ACCOUNTS.ACCOUNT_NUMBER.in(numbers.stream().map(AccountNumber::value).toList());
    }

    @Override
    public void save(Account changed) {
        int updated = db().update(ACCOUNTS)
                .set(ACCOUNTS.BALANCE, changed.balance().value())
                .set(ACCOUNTS.VERSION, changed.version())
                .where(ACCOUNTS.ID.eq(changed.id()).and(ACCOUNTS.VERSION.eq(changed.version() - 1)))
                .execute();
        if (updated == 0) {
            throw ConcurrentModification.of(changed.number());
        }
    }

    @Override
    protected Account toDomain(AccountsRecord r) {
        return new Account(r.getId(), AccountNumber.of(r.getAccountNumber()), r.getCustomerId(),
                Money.of(r.getBalance()), r.getVersion(), r.getCreatedAt());
    }
}
