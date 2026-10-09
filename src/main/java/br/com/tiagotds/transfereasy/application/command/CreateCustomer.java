package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;

public record CreateCustomer(TaxNumber taxNumber, CustomerName name) implements Command<Customer> {
}
