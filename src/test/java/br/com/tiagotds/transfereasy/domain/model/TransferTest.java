package br.com.tiagotds.transfereasy.domain.model;

import static br.com.tiagotds.transfereasy.domain.model.AccountTest.NOW;
import static br.com.tiagotds.transfereasy.domain.model.AccountTest.account;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferTest {

    @Test
    void a_transfer_debits_one_account_credits_the_other_and_links_both_postings() {
        var from = account(1, "A", "100");
        var to = account(2, "B", "5");

        var transfer = Transfer.between(from, to, Money.of("30"), "t-1", NOW);

        assertEquals("t-1", transfer.id());
        assertEquals(Money.of("70"), transfer.debit().account().balance());
        assertEquals(Money.of("35"), transfer.credit().account().balance());

        var out = transfer.debit().posting();
        var in = transfer.credit().posting();
        assertEquals(EntryKind.TRANSFER_OUT, out.kind());
        assertEquals(EntryKind.TRANSFER_IN, in.kind());
        assertEquals(new BigDecimal("-30.00"), out.signedAmount());
        assertEquals(new BigDecimal("30.00"), in.signedAmount());
        assertEquals(AccountNumber.of("B"), out.counterparty());
        assertEquals(AccountNumber.of("A"), in.counterparty());
        assertEquals("t-1", out.transferId());
        assertEquals("t-1", in.transferId());
    }

    @Test
    void money_is_conserved() {
        var transfer = Transfer.between(account(1, "A", "100"), account(2, "B", "5"), Money.of("30"), "t", NOW);

        assertEquals(0, transfer.debit().posting().signedAmount()
                .add(transfer.credit().posting().signedAmount()).signum());
    }

    @Test
    void a_transfer_without_enough_funds_is_refused() {
        assertThrows(InsufficientFunds.class,
                () -> Transfer.between(account(1, "A", "10"), account(2, "B", "0"), Money.of("10.01"), "t", NOW));
    }

    @Test
    void an_account_cannot_transfer_to_itself() {
        var a = account(1, "A", "10");

        assertThrows(InvalidInput.class, () -> Transfer.between(a, a, Money.of("1"), "t", NOW));
    }
}
