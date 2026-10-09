package br.com.tiagotds.transfereasy.application.query;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.Statement;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import br.com.tiagotds.transfereasy.infrastructure.config.StatementSettings;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;

/** Read side: every query is one read-only snapshot, so multi-row results are always mutually consistent. */
public final class AccountQueries {

    private final TransactionRunner tx;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final StatementSettings statements;

    public AccountQueries(TransactionRunner tx, AccountRepository accounts, LedgerRepository ledger,
                          StatementSettings statements) {
        this.tx = tx;
        this.accounts = accounts;
        this.ledger = ledger;
        this.statements = statements;
    }

    public Account get(AccountNumber number) {
        return tx.inReadOnlyTransaction(() -> find(number));
    }

    /** @param requestedSize {@code null} means the configured default */
    public Statement statement(AccountNumber number, Integer requestedSize) {
        int size = requestedSize == null ? statements.defaultSize() : requestedSize;
        if (size < 1 || size > statements.maxSize()) {
            throw new InvalidInput("Parameter 'limit' must be between 1 and " + statements.maxSize() + ".");
        }
        return tx.inReadOnlyTransaction(() -> {
            var account = find(number);
            return new Statement(account, ledger.latest(account.id(), size));
        });
    }

    private Account find(AccountNumber number) {
        return accounts.find(number).orElseThrow(() -> NotFound.account(number));
    }
}
