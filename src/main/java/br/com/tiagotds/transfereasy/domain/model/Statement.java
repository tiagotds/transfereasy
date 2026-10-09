package br.com.tiagotds.transfereasy.domain.model;

import java.util.List;

/** Account snapshot plus its most recent ledger entries (newest first), read from one consistent snapshot. */
public record Statement(Account account, List<LedgerEntry> entries) {
}
