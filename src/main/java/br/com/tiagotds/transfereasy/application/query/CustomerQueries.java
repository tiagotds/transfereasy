package br.com.tiagotds.transfereasy.application.query;

import br.com.tiagotds.transfereasy.domain.error.NotFound;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.TransactionRunner;
import java.util.List;

public final class CustomerQueries {

    private final TransactionRunner tx;
    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public CustomerQueries(TransactionRunner tx, CustomerRepository customers, AccountRepository accounts) {
        this.tx = tx;
        this.customers = customers;
        this.accounts = accounts;
    }

    public Customer get(TaxNumber taxNumber) {
        return tx.inReadOnlyTransaction(() -> customers.find(taxNumber)).orElseThrow(NotFound::customer);
    }

    public List<Customer> search(String nameFragment) {
        return tx.inReadOnlyTransaction(() -> customers.search(nameFragment));
    }

    /** The customer and their accounts from one snapshot. */
    public CustomerAccounts accountsOf(TaxNumber taxNumber) {
        return tx.inReadOnlyTransaction(() -> {
            var customer = customers.find(taxNumber).orElseThrow(NotFound::customer);
            return new CustomerAccounts(customer, accounts.findByCustomer(customer.id()));
        });
    }
}
