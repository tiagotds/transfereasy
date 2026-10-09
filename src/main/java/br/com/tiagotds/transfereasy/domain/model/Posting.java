package br.com.tiagotds.transfereasy.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A ledger line about to be written. {@code signedAmount} is positive for credits and negative for debits, so the
 * sum of all postings always equals the sum of all balances.
 */
public record Posting(long accountId, EntryKind kind, BigDecimal signedAmount, Money balanceAfter,
                      AccountNumber counterparty, String transferId, OffsetDateTime at) {
}
