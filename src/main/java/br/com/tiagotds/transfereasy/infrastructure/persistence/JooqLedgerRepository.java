package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static br.com.tiagotds.transfereasy.jooq.Tables.LEDGER_ENTRIES;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.EntryKind;
import br.com.tiagotds.transfereasy.domain.model.LedgerEntry;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.Posting;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import br.com.tiagotds.transfereasy.jooq.tables.records.LedgerEntriesRecord;
import java.util.List;

public final class JooqLedgerRepository extends JooqRepository<LedgerEntriesRecord, LedgerEntry>
        implements LedgerRepository {

    public JooqLedgerRepository() {
        super(LEDGER_ENTRIES);
    }

    @Override
    public void append(Posting p) {
        db().insertInto(LEDGER_ENTRIES)
                .set(LEDGER_ENTRIES.ACCOUNT_ID, p.accountId())
                .set(LEDGER_ENTRIES.KIND, p.kind().name())
                .set(LEDGER_ENTRIES.AMOUNT, p.signedAmount())
                .set(LEDGER_ENTRIES.BALANCE_AFTER, p.balanceAfter().value())
                .set(LEDGER_ENTRIES.COUNTERPARTY_ACCOUNT_NUMBER, p.counterparty() == null ? null : p.counterparty().value())
                .set(LEDGER_ENTRIES.TRANSFER_ID, p.transferId())
                .set(LEDGER_ENTRIES.CREATED_AT, p.at())
                .execute();
    }

    @Override
    public List<LedgerEntry> latest(long accountId, int limit) {
        return db().selectFrom(LEDGER_ENTRIES).where(LEDGER_ENTRIES.ACCOUNT_ID.eq(accountId))
                .orderBy(LEDGER_ENTRIES.ID.desc()).limit(limit).fetch(this::toDomain);
    }

    @Override
    protected LedgerEntry toDomain(LedgerEntriesRecord r) {
        var counterparty = r.getCounterpartyAccountNumber() == null
                ? null : AccountNumber.of(r.getCounterpartyAccountNumber());
        return new LedgerEntry(r.getId(), r.getAccountId(), EntryKind.valueOf(r.getKind()), r.getAmount(),
                Money.of(r.getBalanceAfter()), counterparty, r.getTransferId(), r.getCreatedAt());
    }
}
