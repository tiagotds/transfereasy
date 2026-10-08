package br.com.tiagotds.transfereasy.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Immutable ledger line. {@code amount} is signed: credits positive, debits negative. */
public record LedgerEntry(long id, long accountId, EntryKind kind, BigDecimal amount, BigDecimal balanceAfter,
                          String counterpartyAccountNumber, String transferId, OffsetDateTime createdAt) {
}
