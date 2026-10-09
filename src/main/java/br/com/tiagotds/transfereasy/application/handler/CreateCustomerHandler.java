package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.application.command.CommandHandler;
import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.port.CustomerRepository;
import java.time.Clock;
import java.time.OffsetDateTime;

public final class CreateCustomerHandler implements CommandHandler<CreateCustomer, Customer> {

    private final CustomerRepository customers;
    private final Clock clock;

    public CreateCustomerHandler(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Override
    public Customer handle(CreateCustomer command) {
        return customers.add(command.taxNumber(), command.name(), OffsetDateTime.now(clock));
    }
}
