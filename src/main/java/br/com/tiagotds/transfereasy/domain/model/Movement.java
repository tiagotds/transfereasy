package br.com.tiagotds.transfereasy.domain.model;

/** The outcome of one balance change: the account's next state and the ledger posting recording it. */
public record Movement(Account account, Posting posting) {
}
