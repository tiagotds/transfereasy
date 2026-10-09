package br.com.tiagotds.transfereasy.application.query;

import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import java.util.List;

public record CustomerAccounts(Customer customer, List<Account> accounts) {
}
