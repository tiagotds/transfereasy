package br.com.tiagotds.transfereasy.application.command;

import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;

public record OpenAccount(TaxNumber customer) implements Command<Account> {
}
