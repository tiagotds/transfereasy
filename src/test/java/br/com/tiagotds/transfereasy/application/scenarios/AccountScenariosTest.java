package br.com.tiagotds.transfereasy.application.scenarios;

import static br.com.tiagotds.transfereasy.support.Money.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.DomainException;
import br.com.tiagotds.transfereasy.domain.error.InsufficientFunds;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.EntryKind;
import br.com.tiagotds.transfereasy.support.TestEnvironment;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountScenariosTest {

    private TestEnvironment env;

    @BeforeEach
    void setUp() {
        env = new TestEnvironment();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private static Class<? extends DomainException> codeOf(Runnable action) {
        return assertThrows(DomainException.class, action::run).getClass();
    }

    @Nested
    class OpeningAccounts {

        @Test
        void a_new_account_starts_with_zero_balance() {
            env.createCustomer("111", "Ada");

            var account = env.open("111");

            assertEquals(of("0"), account.balance().value());
            assertEquals(32, account.number().value().length());
        }

        @Test
        void opening_an_account_for_an_unknown_customer_is_not_found() {
            assertEquals(NotFound.class, codeOf(() -> env.open("missing")));
        }

        @Test
        void opening_an_account_without_tax_number_is_invalid() {
            assertEquals(InvalidInput.class, codeOf(() -> env.open(" ")));
            assertEquals(InvalidInput.class, codeOf(() -> env.open(null)));
        }

        @Test
        void a_customer_can_hold_several_accounts() {
            env.createCustomer("111", "Ada");
            var first = env.open("111");
            var second = env.open("111");

            assertEquals(2, env.accountsOf("111").size());
            assertTrue(!first.number().equals(second.number()));
        }
    }

    @Nested
    class Deposits {

        @Test
        void a_deposit_increases_the_balance_and_is_recorded_in_the_ledger() {
            var number = env.accountWithBalance("111", "0");

            var updated = env.deposit(number, of("25.50"));

            assertEquals(of("25.50"), updated.balance().value());
            var entry = env.statement(number, null).entries().getFirst();
            assertEquals(EntryKind.DEPOSIT, entry.kind());
            assertEquals(of("25.50"), entry.amount());
            assertEquals(of("25.50"), entry.balanceAfter().value());
        }

        @Test
        void a_deposit_into_an_unknown_account_is_not_found() {
            assertEquals(NotFound.class, codeOf(() -> env.deposit("nope", of("1"))));
        }

        @Test
        void invalid_amounts_are_rejected_and_leave_no_trace() {
            var number = env.accountWithBalance("111", "10");

            for (var bad : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("-1"),
                    new BigDecimal("0.001"), new BigDecimal("1000000000000.01")}) {
                assertEquals(InvalidInput.class, codeOf(() -> env.deposit(number, bad)), "amount " + bad);
            }
            assertEquals(of("10"), env.balanceOf(number));
            assertEquals(1, env.statement(number, null).entries().size());
        }

        @Test
        void amounts_with_redundant_trailing_zeros_are_accepted() {
            var number = env.accountWithBalance("111", "0");

            env.deposit(number, new BigDecimal("5.100"));

            assertEquals(of("5.10"), env.balanceOf(number));
        }
    }

    @Nested
    class Withdrawals {

        @Test
        void a_withdrawal_decreases_the_balance_and_records_a_negative_entry() {
            var number = env.accountWithBalance("111", "100");

            var updated = env.withdraw(number, of("40"));

            assertEquals(of("60"), updated.balance().value());
            var entry = env.statement(number, null).entries().getFirst();
            assertEquals(EntryKind.WITHDRAWAL, entry.kind());
            assertEquals(of("-40"), entry.amount());
        }

        @Test
        void withdrawing_the_exact_balance_is_allowed() {
            var number = env.accountWithBalance("111", "100");

            assertEquals(of("0"), env.withdraw(number, of("100")).balance().value());
        }

        @Test
        void withdrawing_more_than_the_balance_is_refused_and_changes_nothing() {
            var number = env.accountWithBalance("111", "100");

            assertEquals(InsufficientFunds.class, codeOf(() -> env.withdraw(number, of("100.01"))));

            assertEquals(of("100"), env.balanceOf(number));
            assertEquals(1, env.statement(number, null).entries().size());
        }

        @Test
        void withdrawing_from_an_unknown_account_is_not_found() {
            assertEquals(NotFound.class, codeOf(() -> env.withdraw("nope", of("1"))));
        }
    }

    @Nested
    class Transfers {

        @Test
        void a_transfer_moves_money_and_writes_two_linked_ledger_entries() {
            var from = env.accountWithBalance("111", "100");
            var to = env.accountWithBalance("222", "5");

            var receipt = env.transfer(from, to, of("30"));

            assertEquals(of("70"), receipt.from().balance().value());
            assertEquals(of("35"), receipt.to().balance().value());
            var out = env.statement(from, null).entries().getFirst();
            var in = env.statement(to, null).entries().getFirst();
            assertEquals(EntryKind.TRANSFER_OUT, out.kind());
            assertEquals(EntryKind.TRANSFER_IN, in.kind());
            assertEquals(receipt.transferId(), out.transferId());
            assertEquals(receipt.transferId(), in.transferId());
            assertEquals(to, out.counterparty().value());
            assertEquals(from, in.counterparty().value());
        }

        @Test
        void a_transfer_without_enough_funds_changes_neither_account() {
            var from = env.accountWithBalance("111", "10");
            var to = env.accountWithBalance("222", "0");

            assertEquals(InsufficientFunds.class, codeOf(() -> env.transfer(from, to, of("10.01"))));

            assertEquals(of("10"), env.balanceOf(from));
            assertEquals(of("0"), env.balanceOf(to));
            assertTrue(env.statement(to, null).entries().isEmpty());
        }

        @Test
        void a_transfer_to_an_unknown_destination_changes_nothing() {
            var from = env.accountWithBalance("111", "10");

            assertEquals(NotFound.class, codeOf(() -> env.transfer(from, "ghost", of("1"))));

            assertEquals(of("10"), env.balanceOf(from));
        }

        @Test
        void a_transfer_from_an_unknown_origin_is_not_found() {
            var to = env.accountWithBalance("222", "0");

            assertEquals(NotFound.class, codeOf(() -> env.transfer("ghost", to, of("1"))));
        }

        @Test
        void transferring_to_the_same_account_is_invalid() {
            var number = env.accountWithBalance("111", "10");

            assertEquals(InvalidInput.class, codeOf(() -> env.transfer(number, number, of("1"))));
        }

        @Test
        void a_missing_destination_is_invalid() {
            var number = env.accountWithBalance("111", "10");

            assertEquals(InvalidInput.class, codeOf(() -> env.transfer(number, null, of("1"))));
            assertEquals(InvalidInput.class, codeOf(() -> env.transfer(number, " ", of("1"))));
        }

        @Test
        void a_transfer_with_an_invalid_amount_is_invalid() {
            var from = env.accountWithBalance("111", "10");
            var to = env.accountWithBalance("222", "0");

            assertEquals(InvalidInput.class, codeOf(() -> env.transfer(from, to, of("0"))));
        }
    }

    @Nested
    class Statements {

        @Test
        void entries_are_returned_newest_first() {
            var number = env.accountWithBalance("111", "0");
            env.deposit(number, of("1"));
            env.deposit(number, of("2"));
            env.withdraw(number, of("0.50"));

            var entries = env.statement(number, null).entries();

            assertEquals(3, entries.size());
            assertEquals(of("-0.50"), entries.get(0).amount());
            assertEquals(of("2"), entries.get(1).amount());
            assertEquals(of("1"), entries.get(2).amount());
            assertEquals(of("2.50"), entries.getFirst().balanceAfter().value());
        }

        @Test
        void the_limit_caps_the_number_of_entries() {
            var number = env.accountWithBalance("111", "0");
            for (int i = 0; i < 5; i++) {
                env.deposit(number, of("1"));
            }

            assertEquals(2, env.statement(number, 2).entries().size());
        }

        @Test
        void an_out_of_range_limit_is_invalid() {
            var number = env.accountWithBalance("111", "0");

            assertEquals(InvalidInput.class, codeOf(() -> env.statement(number, 0)));
            assertEquals(InvalidInput.class, codeOf(() -> env.statement(number, 1001)));
        }

        @Test
        void a_statement_for_an_unknown_account_is_not_found() {
            assertEquals(NotFound.class, codeOf(() -> env.statement("ghost", null)));
        }

        @Test
        void getting_an_unknown_account_is_not_found() {
            assertEquals(NotFound.class, codeOf(() -> env.account("ghost")));
        }
    }
}
