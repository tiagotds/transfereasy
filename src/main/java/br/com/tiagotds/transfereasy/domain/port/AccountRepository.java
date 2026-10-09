package br.com.tiagotds.transfereasy.domain.port;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** All methods run inside the caller's current transaction. */
public interface AccountRepository {

    Account add(Account newAccount);

    Optional<Account> find(AccountNumber number);

    List<Account> findByCustomer(long customerId);

    /**
     * Row-locks the existing accounts among {@code numbers} until the transaction ends and returns them in
     * ascending id order. Locking in one global order is what keeps opposite transfers deadlock-free.
     */
    List<Account> lockAll(Collection<AccountNumber> numbers);

    /**
     * Persists a state produced by the aggregate, but only if nobody else changed the row since it was read
     * (compare-and-set on {@code version}).
     *
     * @throws ConcurrentModification when the stored version is not the one {@code changed} was derived from
     */
    void save(Account changed);
}
