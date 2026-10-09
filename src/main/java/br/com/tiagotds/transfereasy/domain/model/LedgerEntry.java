package br.com.tiagotds.transfereasy.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A persisted, immutable {@link Posting}. */
public record LedgerEntry(long id, long accountId, EntryKind kind, BigDecimal amount, Money balanceAfter,
                          AccountNumber counterparty, String transferId, OffsetDateTime createdAt) {
}
