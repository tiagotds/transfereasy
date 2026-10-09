package br.com.tiagotds.transfereasy.domain.port;

import br.com.tiagotds.transfereasy.domain.error.Conflict;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** All methods run inside the caller's current transaction. */
public interface CustomerRepository {

    /** @throws Conflict when the tax number is already taken; the unique constraint decides concurrent races */
    Customer add(TaxNumber taxNumber, CustomerName name, OffsetDateTime at);

    Optional<Customer> find(TaxNumber taxNumber);

    /** Case-insensitive name search in creation order; a blank fragment matches everyone. */
    List<Customer> search(String nameFragment);
}
