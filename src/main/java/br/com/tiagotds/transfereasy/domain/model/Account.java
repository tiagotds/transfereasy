package br.com.tiagotds.transfereasy.domain.model;

import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import java.time.OffsetDateTime;

/**
 * Account aggregate. Immutable: every movement returns the next state together with the ledger posting that
 * explains it, so a balance can never change without its ledger line.
 *
 * <p>{@code version} increases with every movement; persistence uses it for optimistic concurrency control.
 */
public record Account(long id, AccountNumber number, long customerId, Money balance, long version,
                      OffsetDateTime createdAt) {

    public static Account open(AccountNumber number, long customerId, OffsetDateTime at) {
        return new Account(0, number, customerId, Money.ZERO, 0, at);
    }

    public Movement deposit(Money amount, OffsetDateTime at) {
        return credit(EntryKind.DEPOSIT, amount, null, null, at);
    }

    /** @throws InsufficientFunds when the balance does not cover {@code amount} */
    public Movement withdraw(Money amount, OffsetDateTime at) {
        return debit(EntryKind.WITHDRAWAL, amount, null, null, at);
    }

    Movement credit(EntryKind kind, Money amount, AccountNumber counterparty, String transferId, OffsetDateTime at) {
        var next = withBalance(balance.plus(amount));
        return new Movement(next, new Posting(id, kind, amount.value(), next.balance, counterparty, transferId, at));
    }

    Movement debit(EntryKind kind, Money amount, AccountNumber counterparty, String transferId, OffsetDateTime at) {
        if (balance.isLessThan(amount)) {
            throw new InsufficientFunds();
        }
        var next = withBalance(balance.minus(amount));
        return new Movement(next, new Posting(id, kind, amount.negated(), next.balance, counterparty, transferId, at));
    }

    private Account withBalance(Money newBalance) {
        return new Account(id, number, customerId, newBalance, version + 1, createdAt);
    }
}
