package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.db.TransactionRunner;
import br.com.tiagotds.transfereasy.domain.Account;
import br.com.tiagotds.transfereasy.domain.Customer;
import br.com.tiagotds.transfereasy.domain.DomainException;
import br.com.tiagotds.transfereasy.repository.AccountRepository;
import br.com.tiagotds.transfereasy.repository.CustomerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.exception.IntegrityConstraintViolationException;

public final class CustomerService {

    static final int MAX_NAME = 120;
    static final int MAX_TAX_NUMBER = 32;

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
        var cleanTax = requireText(taxNumber, "taxNumber", MAX_TAX_NUMBER);
        var cleanName = requireText(name, "name", MAX_NAME);
        try {
            return tx.inTransaction(db -> customers.insert(db, cleanTax, cleanName, OffsetDateTime.now(clock)));
        } catch (IntegrityConstraintViolationException e) {
            // the UNIQUE constraint is the arbiter, so two concurrent creations can never both succeed
            throw DomainException.conflict("A customer with tax number '" + cleanTax + "' already exists.");
        }
    }

    public Customer get(String taxNumber) {
        return tx.inReadOnlyTransaction(db -> customers.findByTaxNumber(db, taxNumber))
                .orElseThrow(() -> DomainException.notFound("Customer not found."));
    }

    public List<Customer> search(String nameFragment) {
        return tx.inReadOnlyTransaction(db -> customers.findAll(db, nameFragment));
    }

    public List<Account> accountsOf(String taxNumber) {
        return tx.inReadOnlyTransaction(db -> {
            var customer = customers.findByTaxNumber(db, taxNumber)
                    .orElseThrow(() -> DomainException.notFound("Customer not found."));
            return accounts.findByCustomer(db, customer.id());
        });
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw DomainException.invalid("Field '" + field + "' is required.");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw DomainException.invalid("Field '" + field + "' must have at most " + max + " characters.");
        }
        return trimmed;
    }
}
