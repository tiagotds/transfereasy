package br.com.tiagotds.transfereasy.infrastructure.http;

import br.com.tiagotds.transfereasy.Core;
import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.application.command.DepositMoney;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import br.com.tiagotds.transfereasy.infrastructure.http.Dtos.*;

/** The REST contract: each line binds a path to a command or a query. Contains no business rule. */
public final class ApiRoutes {

    private final Core core;

    public ApiRoutes(Core core) {
        this.core = core;
    }

    public Router build() {
        var bus = core.commands();
        return new Router()
                .add("GET", "/health", r -> HttpResponse.ok(new Health("UP")))

                .add("POST", "/api/customers", CommandRoute.of(bus, CreateCustomerRequest.class,
                        (r, b) -> new CreateCustomer(TaxNumber.of(b.taxNumber()), CustomerName.of(b.name())),
                        CustomerResponse::of))
                .add("GET", "/api/customers", r -> HttpResponse.ok(
                        core.customers().search(r.queryParam("name")).stream().map(CustomerResponse::of).toList()))
                .add("GET", "/api/customers/{taxNumber}", r -> HttpResponse.ok(
                        CustomerResponse.of(core.customers().get(taxNumber(r)))))
                .add("GET", "/api/customers/{taxNumber}/accounts", r -> HttpResponse.ok(
                        CustomerAccountsResponse.of(core.customers().accountsOf(taxNumber(r)))))

                .add("POST", "/api/accounts", CommandRoute.of(bus, OpenAccountRequest.class,
                        (r, b) -> new OpenAccount(TaxNumber.of(b.taxNumber())), AccountResponse::of))
                .add("GET", "/api/accounts/{number}", r -> HttpResponse.ok(
                        AccountResponse.of(core.accounts().get(accountNumber(r)))))
                .add("GET", "/api/accounts/{number}/statement", r -> HttpResponse.ok(
                        StatementResponse.of(core.accounts().statement(accountNumber(r), intParam(r, "limit")))))
                .add("POST", "/api/accounts/{number}/deposits", CommandRoute.of(bus, AmountRequest.class,
                        (r, b) -> new DepositMoney(accountNumber(r), b.amount()), AccountResponse::of))
                .add("POST", "/api/accounts/{number}/withdrawals", CommandRoute.of(bus, AmountRequest.class,
                        (r, b) -> new WithdrawMoney(accountNumber(r), b.amount()), AccountResponse::of))
                .add("POST", "/api/accounts/{number}/transfers", CommandRoute.of(bus, TransferRequest.class,
                        (r, b) -> new TransferMoney(accountNumber(r), AccountNumber.of(b.toAccountNumber(),
                                "toAccountNumber"), b.amount()),
                        TransferResponse::of));
    }

    public record Health(String status) {
    }

    private static TaxNumber taxNumber(HttpRequest r) {
        return TaxNumber.of(r.pathParam("taxNumber"));
    }

    private static AccountNumber accountNumber(HttpRequest r) {
        return AccountNumber.of(r.pathParam("number"));
    }

    private static Integer intParam(HttpRequest r, String name) {
        var raw = r.queryParam(name);
        if (raw == null) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw new InvalidInput("Parameter '" + name + "' must be an integer.");
        }
    }
}
