package br.com.tiagotds.transfereasy.http;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.http.Dtos.*;
import br.com.tiagotds.transfereasy.Core;
import br.com.tiagotds.transfereasy.application.command.CreateCustomer;
import br.com.tiagotds.transfereasy.application.command.DepositMoney;
import br.com.tiagotds.transfereasy.application.command.OpenAccount;
import br.com.tiagotds.transfereasy.application.command.TransferMoney;
import br.com.tiagotds.transfereasy.application.command.WithdrawMoney;
import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.CustomerName;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;

/** Binds the REST contract to the services. Contains no business rule. */
public final class ApiRoutes {

    private final Core core;

    public ApiRoutes(Core core) {
        this.core = core;
    }

    public Router build() {
        return new Router()
                .add("GET", "/health", r -> HttpResponse.ok(new Health("UP")))
                .add("POST", "/api/customers", this::createCustomer)
                .add("GET", "/api/customers", r -> HttpResponse.ok(core.customers().search(r.queryParam("name")).stream()
                        .map(CustomerResponse::of).toList()))
                .add("GET", "/api/customers/{taxNumber}", r ->
                        HttpResponse.ok(CustomerResponse.of(core.customers().get(TaxNumber.of(r.pathParam("taxNumber"))))))
                .add("GET", "/api/customers/{taxNumber}/accounts", this::customerAccounts)
                .add("POST", "/api/accounts", this::openAccount)
                .add("GET", "/api/accounts/{number}", r ->
                        HttpResponse.ok(AccountResponse.of(core.accounts().get(AccountNumber.of(r.pathParam("number"))))))
                .add("GET", "/api/accounts/{number}/statement", this::statement)
                .add("POST", "/api/accounts/{number}/deposits", this::deposit)
                .add("POST", "/api/accounts/{number}/withdrawals", this::withdraw)
                .add("POST", "/api/accounts/{number}/transfers", this::transfer);
    }

    public record Health(String status) {
    }

    private HttpResponse createCustomer(HttpRequest r) {
        var req = body(r, CreateCustomerRequest.class);
        return HttpResponse.created(CustomerResponse.of(core.commands().execute(
                new CreateCustomer(TaxNumber.of(req.taxNumber()), CustomerName.of(req.name())))));
    }

    private HttpResponse customerAccounts(HttpRequest r) {
        var result = core.customers().accountsOf(TaxNumber.of(r.pathParam("taxNumber")));
        var list = result.accounts().stream().map(AccountResponse::of).toList();
        return HttpResponse.ok(new CustomerAccountsResponse(CustomerResponse.of(result.customer()), list));
    }

    private HttpResponse openAccount(HttpRequest r) {
        var req = body(r, OpenAccountRequest.class);
        return HttpResponse.created(AccountResponse.of(core.commands().execute(
                new OpenAccount(TaxNumber.of(req.taxNumber())))));
    }

    private HttpResponse statement(HttpRequest r) {
        var statement = core.accounts().statement(AccountNumber.of(r.pathParam("number")), intParam(r, "limit"));
        return HttpResponse.ok(new StatementResponse(AccountResponse.of(statement.account()),
                statement.entries().stream().map(EntryResponse::of).toList()));
    }

    private HttpResponse deposit(HttpRequest r) {
        var req = body(r, AmountRequest.class);
        return HttpResponse.created(AccountResponse.of(core.commands().execute(
                new DepositMoney(AccountNumber.of(r.pathParam("number")), req.amount()))));
    }

    private HttpResponse withdraw(HttpRequest r) {
        var req = body(r, AmountRequest.class);
        return HttpResponse.created(AccountResponse.of(core.commands().execute(
                new WithdrawMoney(AccountNumber.of(r.pathParam("number")), req.amount()))));
    }

    private HttpResponse transfer(HttpRequest r) {
        var req = body(r, TransferRequest.class);
        var receipt = core.commands().execute(new TransferMoney(AccountNumber.of(r.pathParam("number")),
                AccountNumber.of(req.toAccountNumber(), "toAccountNumber"), req.amount()));
        return HttpResponse.created(new TransferResponse(receipt.transferId(),
                AccountResponse.of(receipt.from()), AccountResponse.of(receipt.to())));
    }

    private static <T> T body(HttpRequest r, Class<T> type) {
        try {
            return Json.read(r.body(), type);
        } catch (Json.InvalidJsonException e) {
            throw new InvalidInput(e.getMessage());
        }
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
