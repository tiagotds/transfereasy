package br.com.tiagotds.transfereasy.domain.port;

import br.com.tiagotds.transfereasy.domain.model.LedgerEntry;
import br.com.tiagotds.transfereasy.domain.model.Posting;
import java.util.List;

/** Append-only ledger. All methods run inside the caller's current transaction. */
public interface LedgerRepository {

    void append(Posting posting);

    /** Newest first; the id breaks ties so the order is total. */
    List<LedgerEntry> latest(long accountId, int limit);
}
