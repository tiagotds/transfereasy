package br.com.tiagotds.transfereasy.infrastructure.persistence;

import static br.com.tiagotds.transfereasy.jooq.Tables.CUSTOMERS;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import br.com.tiagotds.transfereasy.jooq.tables.records.CustomersRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;

public final class JooqCustomerRepository extends JooqRepository<CustomersRecord, Customer>
        implements CustomerRepository {

    public JooqCustomerRepository() {
        super(CUSTOMERS);
    }

    @Override
    public Customer add(TaxNumber taxNumber, CustomerName name, OffsetDateTime at) {
        try {
            return insert(Map.of(CUSTOMERS.TAX_NUMBER, taxNumber.value(), CUSTOMERS.NAME, name.value(),
                    CUSTOMERS.CREATED_AT, at));
        } catch (IntegrityConstraintViolationException e) {
            throw Conflict.duplicateCustomer(taxNumber);
        }
    }

    @Override
    public Optional<Customer> find(TaxNumber taxNumber) {
        return findOne(CUSTOMERS.TAX_NUMBER.eq(taxNumber.value()));
    }

    @Override
    public List<Customer> search(String nameFragment) {
        var condition = nameFragment == null || nameFragment.isBlank()
                ? DSL.noCondition()
                : CUSTOMERS.NAME.containsIgnoreCase(nameFragment.trim());
        return findAll(condition, CUSTOMERS.ID);
    }

    @Override
    protected Customer toDomain(CustomersRecord r) {
        return new Customer(r.getId(), TaxNumber.of(r.getTaxNumber()), CustomerName.of(r.getName()), r.getCreatedAt());
    }
}
