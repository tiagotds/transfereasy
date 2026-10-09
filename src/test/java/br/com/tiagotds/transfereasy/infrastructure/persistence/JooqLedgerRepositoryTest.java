package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.EntryKind;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.model.Transfer;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JooqLedgerRepositoryTest extends RepositoryTestBase {

    private final JooqLedgerRepository ledger = new JooqLedgerRepository();
    private Account a;
    private Account b;

    @BeforeEach
    void accounts() {
        var customerId = tx(() -> new JooqCustomerRepository().add(TaxNumber.of("1"), CustomerName.of("Ada"), NOW)).id();
        var repository = new JooqAccountRepository();
        a = tx(() -> repository.add(Account.open(AccountNumber.of("A"), customerId, NOW)));
        b = tx(() -> repository.add(Account.open(AccountNumber.of("B"), customerId, NOW)));
    }

    @Test
    void postings_are_read_back_newest_first_and_capped() {
        run(() -> {
            ledger.append(a.deposit(Money.of("1"), NOW).posting());
            ledger.append(a.deposit(Money.of("2"), NOW).posting());
            ledger.append(a.deposit(Money.of("3"), NOW).posting());
        });

        var latest = tx(() -> ledger.latest(a.id(), 2));

        assertEquals(2, latest.size());
        assertEquals(new BigDecimal("3.00"), latest.get(0).amount());
        assertEquals(new BigDecimal("2.00"), latest.get(1).amount());
        assertNull(latest.get(0).counterparty());
    }

    @Test
    void transfer_postings_keep_kind_counterparty_and_transfer_id() {
        var funded = a.deposit(Money.of("10"), NOW).account();
        var transfer = Transfer.between(funded, b, Money.of("4"), "t-1", NOW);
        run(() -> {
            ledger.append(transfer.debit().posting());
            ledger.append(transfer.credit().posting());
        });

        var out = tx(() -> ledger.latest(a.id(), 1)).getFirst();

        assertEquals(EntryKind.TRANSFER_OUT, out.kind());
        assertEquals(new BigDecimal("-4.00"), out.amount());
        assertEquals(Money.of("6"), out.balanceAfter());
        assertEquals(AccountNumber.of("B"), out.counterparty());
        assertEquals("t-1", out.transferId());
    }
}
