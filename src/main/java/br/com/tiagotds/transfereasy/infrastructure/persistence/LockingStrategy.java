package br.com.tiagotds.transfereasy.infrastructure.persistence;

import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import java.util.Collection;
import java.util.List;

/**
 * How the accounts a movement will change are loaded (Strategy pattern, chosen by {@code account.locking}).
 * Everything after loading is identical: the aggregate decides, and {@link AccountRepository#save} writes with a
 * version compare-and-set. Both strategies return accounts in ascending id order, so writes, and therefore row
 * locks, are always taken in one global order and opposite transfers cannot deadlock.
 */
public enum LockingStrategy {

    /** Row-locks on read: competitors queue, so the version check never fails. Best under high contention. */
    PESSIMISTIC {
        @Override
        public List<Account> load(AccountRepository accounts, Collection<AccountNumber> numbers) {
            return accounts.lockAll(numbers);
        }
    },

    /** Reads without locking; a competitor that wrote first makes our save fail and the retry re-runs us. */
    OPTIMISTIC {
        @Override
        public List<Account> load(AccountRepository accounts, Collection<AccountNumber> numbers) {
            return accounts.findAll(numbers);
        }
    };

    public abstract List<Account> load(AccountRepository accounts, Collection<AccountNumber> numbers);
}
