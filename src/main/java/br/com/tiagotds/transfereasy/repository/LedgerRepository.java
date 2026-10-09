package br.com.tiagotds.transfereasy.repository;

import static br.com.tiagotds.transfereasy.jooq.Tables.LEDGER_ENTRIES;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.EntryKind;
import br.com.tiagotds.transfereasy.domain.model.LedgerEntry;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.jooq.tables.records.LedgerEntriesRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;

public final class LedgerRepository {

    public void append(DSLContext tx, long accountId, EntryKind kind, BigDecimal signedAmount,
                       BigDecimal balanceAfter, String counterparty, String transferId, OffsetDateTime now) {
        tx.insertInto(LEDGER_ENTRIES)
                .set(LEDGER_ENTRIES.ACCOUNT_ID, accountId)
                .set(LEDGER_ENTRIES.KIND, kind.name())
                .set(LEDGER_ENTRIES.AMOUNT, signedAmount)
                .set(LEDGER_ENTRIES.BALANCE_AFTER, balanceAfter)
                .set(LEDGER_ENTRIES.COUNTERPARTY_ACCOUNT_NUMBER, counterparty)
                .set(LEDGER_ENTRIES.TRANSFER_ID, transferId)
                .set(LEDGER_ENTRIES.CREATED_AT, now)
                .execute();
    }

    /** Newest first. The id is the tie-breaker, so ordering is total even for entries in the same instant. */
    public List<LedgerEntry> findLatest(DSLContext tx, long accountId, int limit) {
        return tx.selectFrom(LEDGER_ENTRIES).where(LEDGER_ENTRIES.ACCOUNT_ID.eq(accountId))
                .orderBy(LEDGER_ENTRIES.ID.desc()).limit(limit).fetch(LedgerRepository::toDomain);
    }

    private static LedgerEntry toDomain(LedgerEntriesRecord r) {
        var counterparty = r.getCounterpartyAccountNumber() == null ? null : AccountNumber.of(r.getCounterpartyAccountNumber());
        return new LedgerEntry(r.getId(), r.getAccountId(), EntryKind.valueOf(r.getKind()), r.getAmount(),
                Money.of(r.getBalanceAfter()), counterparty, r.getTransferId(), r.getCreatedAt());
    }
}
