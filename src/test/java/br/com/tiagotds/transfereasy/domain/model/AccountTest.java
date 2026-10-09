package br.com.tiagotds.transfereasy.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AccountTest {

    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-15T10:00:00Z");

    static Account account(long id, String number, String balance) {
        return new Account(id, AccountNumber.of(number), 1L, Money.of(balance), 7L, NOW);
    }

    @Test
    void a_deposit_credits_the_balance_bumps_the_version_and_produces_a_ledger_posting() {
        var account = account(1, "A", "10");

        var movement = account.deposit(Money.of("2.50"), NOW);

        assertEquals(Money.of("12.50"), movement.account().balance());
        assertEquals(8L, movement.account().version());
        var posting = movement.posting();
        assertEquals(EntryKind.DEPOSIT, posting.kind());
        assertEquals(1L, posting.accountId());
        assertEquals(new BigDecimal("2.50"), posting.signedAmount());
        assertEquals(Money.of("12.50"), posting.balanceAfter());
        assertNull(posting.counterparty());
        assertNull(posting.transferId());
        assertEquals(NOW, posting.at());
    }

    @Test
    void a_withdrawal_debits_the_balance_and_its_posting_is_negative() {
        var movement = account(1, "A", "10").withdraw(Money.of("4"), NOW);

        assertEquals(Money.of("6"), movement.account().balance());
        assertEquals(EntryKind.WITHDRAWAL, movement.posting().kind());
        assertEquals(new BigDecimal("-4.00"), movement.posting().signedAmount());
    }

    @Test
    void the_whole_balance_can_be_withdrawn() {
        assertEquals(Money.ZERO, account(1, "A", "10").withdraw(Money.of("10"), NOW).account().balance());
    }

    @Test
    void withdrawing_more_than_the_balance_is_refused() {
        var account = account(1, "A", "10");

        assertThrows(InsufficientFunds.class, () -> account.withdraw(Money.of("10.01"), NOW));
    }

    @Test
    void the_aggregate_is_immutable_every_movement_returns_a_new_state() {
        var account = account(1, "A", "10");

        account.deposit(Money.of("1"), NOW);

        assertEquals(Money.of("10"), account.balance());
        assertEquals(7L, account.version());
    }

    @Test
    void entry_kinds_know_their_direction() {
        assertEquals(EntryKind.Direction.CREDIT, EntryKind.DEPOSIT.direction());
        assertEquals(EntryKind.Direction.CREDIT, EntryKind.TRANSFER_IN.direction());
        assertEquals(EntryKind.Direction.DEBIT, EntryKind.WITHDRAWAL.direction());
        assertEquals(EntryKind.Direction.DEBIT, EntryKind.TRANSFER_OUT.direction());
    }
}
