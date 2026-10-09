package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.exception.IntegrityConstraintViolationException;

public final class CustomerService {

    private final TransactionRunner tx;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final Clock clock;

    public CustomerService(TransactionRunner tx, CustomerRepository customers, AccountRepository accounts,
                           Clock clock) {
        this.tx = tx;
        this.customers = customers;
        this.accounts = accounts;
        this.clock = clock;
    }

    public Customer create(String taxNumber, String name) {
        var cleanTax = TaxNumber.of(taxNumber);
        var cleanName = CustomerName.of(name);
        try {
            return tx.inTransaction(db -> customers.insert(db, cleanTax.value(), cleanName.value(),
                    OffsetDateTime.now(clock)));
        } catch (IntegrityConstraintViolationException e) {
            // the UNIQUE constraint is the arbiter, so two concurrent creations can never both succeed
            throw Conflict.duplicateCustomer(cleanTax);
        }
    }

    public Customer get(String taxNumber) {
        return tx.inReadOnlyTransaction(db -> customers.findByTaxNumber(db, taxNumber))
                .orElseThrow(NotFound::customer);
    }

    public List<Customer> search(String nameFragment) {
        return tx.inReadOnlyTransaction(db -> customers.findAll(db, nameFragment));
    }

    public List<Account> accountsOf(String taxNumber) {
        return tx.inReadOnlyTransaction(db -> {
            var customer = customers.findByTaxNumber(db, taxNumber)
                    .orElseThrow(NotFound::customer);
            return accounts.findByCustomer(db, customer.id());
        });
    }
}
