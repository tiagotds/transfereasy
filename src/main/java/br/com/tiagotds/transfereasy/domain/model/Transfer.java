package br.com.tiagotds.transfereasy.domain.model;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.time.OffsetDateTime;

/**
 * Domain service for moving money between two accounts: one debit and one credit sharing a transfer id. Either
 * both movements exist or neither does; the caller persists them in a single transaction.
 */
public record Transfer(String id, Movement debit, Movement credit) {

    public static Transfer between(Account from, Account to, Money amount, String id, OffsetDateTime at) {
        if (from.number().equals(to.number())) {
            throw new InvalidInput("Origin and destination accounts must be different.");
        }
        var debit = from.debit(EntryKind.TRANSFER_OUT, amount, to.number(), id, at);
        var credit = to.credit(EntryKind.TRANSFER_IN, amount, from.number(), id, at);
        return new Transfer(id, debit, credit);
    }
}
