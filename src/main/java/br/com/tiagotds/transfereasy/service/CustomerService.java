package br.com.tiagotds.transfereasy.service;

import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

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
        var tax = TaxNumber.of(taxNumber);
        var customerName = CustomerName.of(name);
        return tx.inTransaction(() -> customers.add(tax, customerName, OffsetDateTime.now(clock)));
    }

    public Customer get(String taxNumber) {
        var tax = TaxNumber.of(taxNumber);
        return tx.inReadOnlyTransaction(() -> customers.find(tax)).orElseThrow(NotFound::customer);
    }

    public List<Customer> search(String nameFragment) {
        return tx.inReadOnlyTransaction(() -> customers.search(nameFragment));
    }

    public List<Account> accountsOf(String taxNumber) {
        var tax = TaxNumber.of(taxNumber);
        return tx.inReadOnlyTransaction(() -> {
            var customer = customers.find(tax).orElseThrow(NotFound::customer);
            return accounts.findByCustomer(customer.id());
        });
    }
}
