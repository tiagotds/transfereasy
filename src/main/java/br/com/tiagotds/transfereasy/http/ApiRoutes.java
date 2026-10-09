package br.com.tiagotds.transfereasy.http;

import br.com.tiagotds.transfereasy.domain.error.InvalidInput;
import br.com.tiagotds.transfereasy.http.Dtos.*;
import br.com.tiagotds.transfereasy.service.AccountService;
import br.com.tiagotds.transfereasy.service.CustomerService;

/** Binds the REST contract to the services. Contains no business rule. */
public final class ApiRoutes {

    private final CustomerService customers;
    private final AccountService accounts;

    public ApiRoutes(CustomerService customers, AccountService accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    public Router build() {
        return new Router()
                .add("GET", "/health", r -> HttpResponse.ok(new Health("UP")))
                .add("POST", "/api/customers", this::createCustomer)
                .add("GET", "/api/customers", r -> HttpResponse.ok(customers.search(r.queryParam("name")).stream()
                        .map(CustomerResponse::of).toList()))
                .add("GET", "/api/customers/{taxNumber}", r ->
                        HttpResponse.ok(CustomerResponse.of(customers.get(r.pathParam("taxNumber")))))
                .add("GET", "/api/customers/{taxNumber}/accounts", this::customerAccounts)
                .add("POST", "/api/accounts", this::openAccount)
                .add("GET", "/api/accounts/{number}", r ->
                        HttpResponse.ok(AccountResponse.of(accounts.get(r.pathParam("number")))))
                .add("GET", "/api/accounts/{number}/statement", this::statement)
                .add("POST", "/api/accounts/{number}/deposits", this::deposit)
                .add("POST", "/api/accounts/{number}/withdrawals", this::withdraw)
                .add("POST", "/api/accounts/{number}/transfers", this::transfer);
    }

    public record Health(String status) {
    }

    private HttpResponse createCustomer(HttpRequest r) {
        var req = body(r, CreateCustomerRequest.class);
        return HttpResponse.created(CustomerResponse.of(customers.create(req.taxNumber(), req.name())));
    }

    private HttpResponse customerAccounts(HttpRequest r) {
        var taxNumber = r.pathParam("taxNumber");
        var customer = customers.get(taxNumber);
        var list = customers.accountsOf(taxNumber).stream().map(AccountResponse::of).toList();
        return HttpResponse.ok(new CustomerAccountsResponse(CustomerResponse.of(customer), list));
    }

    private HttpResponse openAccount(HttpRequest r) {
        var req = body(r, OpenAccountRequest.class);
        return HttpResponse.created(AccountResponse.of(accounts.open(req.taxNumber())));
    }

    private HttpResponse statement(HttpRequest r) {
        var statement = accounts.statement(r.pathParam("number"), intParam(r, "limit"));
        return HttpResponse.ok(new StatementResponse(AccountResponse.of(statement.account()),
                statement.entries().stream().map(EntryResponse::of).toList()));
    }

    private HttpResponse deposit(HttpRequest r) {
        var req = body(r, AmountRequest.class);
        return HttpResponse.created(AccountResponse.of(accounts.deposit(r.pathParam("number"), req.amount())));
    }

    private HttpResponse withdraw(HttpRequest r) {
        var req = body(r, AmountRequest.class);
        return HttpResponse.created(AccountResponse.of(accounts.withdraw(r.pathParam("number"), req.amount())));
    }

    private HttpResponse transfer(HttpRequest r) {
        var req = body(r, TransferRequest.class);
        var receipt = accounts.transfer(r.pathParam("number"), req.toAccountNumber(), req.amount());
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
