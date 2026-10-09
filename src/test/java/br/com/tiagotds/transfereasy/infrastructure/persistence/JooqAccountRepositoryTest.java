package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.domain.error.ConcurrentModification;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.Money;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JooqAccountRepositoryTest extends RepositoryTestBase {

    private final JooqAccountRepository accounts = new JooqAccountRepository();
    private long customerId;

    @BeforeEach
    void customer() {
        customerId = tx(() -> new JooqCustomerRepository().add(TaxNumber.of("1"), CustomerName.of("Ada"), NOW)).id();
    }

    private Account open(String number) {
        return tx(() -> accounts.add(Account.open(AccountNumber.of(number), customerId, NOW)));
    }

    private Account reload(String number) {
        return tx(() -> accounts.find(AccountNumber.of(number))).orElseThrow();
    }

    @Test
    void an_opened_account_is_persisted_with_zero_balance_and_version_zero() {
        var opened = open("A");

        assertTrue(opened.id() > 0);
        assertEquals(Money.ZERO, opened.balance());
        assertEquals(0L, opened.version());
        assertEquals(opened, reload("A"));
    }

    @Test
    void accounts_of_a_customer_are_listed_in_opening_order() {
        open("B");
        open("A");

        var numbers = tx(() -> accounts.findByCustomer(customerId)).stream().map(a -> a.number().value()).toList();

        assertEquals(List.of("B", "A"), numbers);
    }

    @Test
    void lock_all_returns_the_existing_accounts_in_id_order_whatever_the_requested_order() {
        var first = open("B");
        var second = open("A");

        var locked = tx(() -> accounts.lockAll(List.of(AccountNumber.of("A"), AccountNumber.of("ghost"),
                AccountNumber.of("B"))));

        assertEquals(List.of(first.id(), second.id()), locked.stream().map(Account::id).toList());
    }

    @Test
    void find_all_reads_without_locking_in_id_order() {
        var first = open("B");
        var second = open("A");

        var found = tx(() -> accounts.findAll(List.of(AccountNumber.of("A"), AccountNumber.of("B"))));

        assertEquals(List.of(first.id(), second.id()), found.stream().map(Account::id).toList());
    }

    @Test
    void saving_a_movement_persists_the_new_balance_and_version() {
        open("A");
        var moved = reload("A").deposit(Money.of("10"), NOW).account();

        run(() -> accounts.save(moved));

        assertEquals(Money.of("10"), reload("A").balance());
        assertEquals(1L, reload("A").version());
    }

    @Test
    void saving_a_state_derived_from_a_stale_version_is_a_concurrent_modification() {
        open("A");
        var stale = reload("A");
        run(() -> accounts.save(stale.deposit(Money.of("10"), NOW).account()));

        var lostUpdate = stale.deposit(Money.of("99"), NOW).account();

        assertThrows(ConcurrentModification.class, () -> run(() -> accounts.save(lostUpdate)));
        assertEquals(Money.of("10"), reload("A").balance(), "the concurrent update must not be overwritten");
    }
}
