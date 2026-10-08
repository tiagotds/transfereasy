package br.com.tiagotds.transfereasy.repository;

import static br.com.tiagotds.transfereasy.jooq.Tables.CUSTOMERS;

import br.com.tiagotds.transfereasy.domain.Customer;
import br.com.tiagotds.transfereasy.jooq.tables.records.CustomersRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;

public final class CustomerRepository {

    public Customer insert(DSLContext tx, String taxNumber, String name, OffsetDateTime now) {
        return toDomain(tx.insertInto(CUSTOMERS)
                .set(CUSTOMERS.TAX_NUMBER, taxNumber)
                .set(CUSTOMERS.NAME, name)
                .set(CUSTOMERS.CREATED_AT, now)
                .returning()
                .fetchSingle());
    }

    public Optional<Customer> findByTaxNumber(DSLContext tx, String taxNumber) {
        return tx.selectFrom(CUSTOMERS).where(CUSTOMERS.TAX_NUMBER.eq(taxNumber))
                .fetchOptional().map(CustomerRepository::toDomain);
    }

    public List<Customer> findAll(DSLContext tx, String nameFragment) {
        var query = tx.selectFrom(CUSTOMERS);
        var filtered = nameFragment == null || nameFragment.isBlank()
                ? query.orderBy(CUSTOMERS.ID)
                : query.where(CUSTOMERS.NAME.containsIgnoreCase(nameFragment.trim())).orderBy(CUSTOMERS.ID);
        return filtered.fetch(CustomerRepository::toDomain);
    }

    private static Customer toDomain(CustomersRecord r) {
        return new Customer(r.getId(), r.getTaxNumber(), r.getName(), r.getCreatedAt());
    }
}
