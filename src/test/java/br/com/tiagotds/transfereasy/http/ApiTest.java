package br.com.tiagotds.transfereasy.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.Application;
import br.com.tiagotds.transfereasy.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** End-to-end tests over real HTTP against a real application instance on an ephemeral port. */
class ApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Application app;
    private static HttpClient client;
    private static String base;
    private static int sequence;

    record Reply(int status, JsonNode body, String allow) {
    }

    @BeforeAll
    static void start() throws Exception {
        app = Application.start(new AppConfig(0, 32));
        client = HttpClient.newHttpClient();
        base = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private static Reply call(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        var response = client.send(builder.build(), BodyHandlers.ofString());
        return new Reply(response.statusCode(), JSON.readTree(response.body()),
                response.headers().firstValue("Allow").orElse(null));
    }

    private static synchronized String uniqueTax() {
        return "TAX-" + (++sequence);
    }

    private static String newCustomerWithAccount(String balance) throws Exception {
        var tax = uniqueTax();
        assertEquals(201, call("POST", "/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"Person " + tax + "\"}").status());
        var account = call("POST", "/api/accounts", "{\"taxNumber\":\"" + tax + "\"}").body().get("number").asText();
        if (!balance.equals("0")) {
            assertEquals(201, call("POST", "/api/accounts/" + account + "/deposits", "{\"amount\":" + balance + "}").status());
        }
        return account;
    }

    @Test
    void health_endpoint_reports_up() throws Exception {
        var reply = call("GET", "/health", null);

        assertEquals(200, reply.status());
        assertEquals("UP", reply.body().get("status").asText());
    }

    @Test
    void customer_lifecycle_create_get_search_and_list_accounts() throws Exception {
        var tax = uniqueTax();

        var created = call("POST", "/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"Grace Hopper\"}");
        assertEquals(201, created.status());
        assertEquals("Grace Hopper", created.body().get("name").asText());

        assertEquals(200, call("GET", "/api/customers/" + tax, null).status());
        var search = call("GET", "/api/customers?name=hopper", null);
        assertEquals(200, search.status());
        assertTrue(search.body().size() >= 1);

        call("POST", "/api/accounts", "{\"taxNumber\":\"" + tax + "\"}");
        var accounts = call("GET", "/api/customers/" + tax + "/accounts", null);
        assertEquals(200, accounts.status());
        assertEquals(1, accounts.body().get("accounts").size());
        assertEquals(tax, accounts.body().get("customer").get("taxNumber").asText());
    }

    @Test
    void duplicate_customer_returns_409() throws Exception {
        var tax = uniqueTax();
        call("POST", "/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"A\"}");

        var again = call("POST", "/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"B\"}");

        assertEquals(409, again.status());
        assertEquals("CONFLICT", again.body().get("code").asText());
    }

    @Test
    void unknown_customer_and_account_return_404() throws Exception {
        assertEquals(404, call("GET", "/api/customers/ghost", null).status());
        assertEquals(404, call("GET", "/api/customers/ghost/accounts", null).status());
        assertEquals(404, call("GET", "/api/accounts/ghost", null).status());
        assertEquals(404, call("GET", "/api/accounts/ghost/statement", null).status());
        assertEquals(404, call("POST", "/api/accounts", "{\"taxNumber\":\"ghost\"}").status());
        assertEquals(404, call("GET", "/nothing/here", null).status());
    }

    @Test
    void deposit_withdraw_and_statement_flow() throws Exception {
        var account = newCustomerWithAccount("0");

        var deposit = call("POST", "/api/accounts/" + account + "/deposits", "{\"amount\":100.50}");
        assertEquals(201, deposit.status());
        assertEquals(0, new BigDecimal("100.50").compareTo(deposit.body().get("balance").decimalValue()));

        var withdrawal = call("POST", "/api/accounts/" + account + "/withdrawals", "{\"amount\":40.25}");
        assertEquals(201, withdrawal.status());
        assertEquals(0, new BigDecimal("60.25").compareTo(withdrawal.body().get("balance").decimalValue()));

        var statement = call("GET", "/api/accounts/" + account + "/statement", null);
        assertEquals(200, statement.status());
        var entries = statement.body().get("entries");
        assertEquals(2, entries.size());
        assertEquals("WITHDRAWAL", entries.get(0).get("kind").asText());
        assertEquals("DEPOSIT", entries.get(1).get("kind").asText());
        assertEquals(1, call("GET", "/api/accounts/" + account + "/statement?limit=1", null).body().get("entries").size());
        assertEquals(400, call("GET", "/api/accounts/" + account + "/statement?limit=abc", null).status());
        assertEquals(400, call("GET", "/api/accounts/" + account + "/statement?limit=0", null).status());
    }

    @Test
    void overdraft_returns_422_and_keeps_the_balance() throws Exception {
        var account = newCustomerWithAccount("10");

        var reply = call("POST", "/api/accounts/" + account + "/withdrawals", "{\"amount\":10.01}");

        assertEquals(422, reply.status());
        assertEquals("INSUFFICIENT_FUNDS", reply.body().get("code").asText());
        assertEquals(0, BigDecimal.TEN.compareTo(call("GET", "/api/accounts/" + account, null).body().get("balance").decimalValue()));
    }

    @Test
    void transfer_moves_money_and_reports_both_sides() throws Exception {
        var from = newCustomerWithAccount("100");
        var to = newCustomerWithAccount("0");

        var reply = call("POST", "/api/accounts/" + from + "/transfers",
                "{\"toAccountNumber\":\"" + to + "\",\"amount\":25}");

        assertEquals(201, reply.status());
        assertNotNull(reply.body().get("transferId"));
        assertEquals(0, new BigDecimal("75").compareTo(reply.body().get("from").get("balance").decimalValue()));
        assertEquals(0, new BigDecimal("25").compareTo(reply.body().get("to").get("balance").decimalValue()));
    }

    @Test
    void transfer_error_cases() throws Exception {
        var from = newCustomerWithAccount("10");
        var to = newCustomerWithAccount("0");

        assertEquals(422, call("POST", "/api/accounts/" + from + "/transfers",
                "{\"toAccountNumber\":\"" + to + "\",\"amount\":11}").status());
        assertEquals(404, call("POST", "/api/accounts/" + from + "/transfers",
                "{\"toAccountNumber\":\"ghost\",\"amount\":1}").status());
        assertEquals(404, call("POST", "/api/accounts/ghost/transfers",
                "{\"toAccountNumber\":\"" + to + "\",\"amount\":1}").status());
        assertEquals(400, call("POST", "/api/accounts/" + from + "/transfers",
                "{\"toAccountNumber\":\"" + from + "\",\"amount\":1}").status());
        assertEquals(400, call("POST", "/api/accounts/" + from + "/transfers", "{\"amount\":1}").status());
    }

    @Test
    void malformed_and_invalid_payloads_return_400() throws Exception {
        var account = newCustomerWithAccount("0");
        var deposits = "/api/accounts/" + account + "/deposits";

        assertEquals(400, call("POST", deposits, "not json").status());
        assertEquals(400, call("POST", deposits, "").status());
        assertEquals(400, call("POST", deposits, "{\"amount\":\"abc\"}").status());
        assertEquals(400, call("POST", deposits, "{\"amount\":5,\"extra\":1}").status());
        assertEquals(400, call("POST", deposits, "{\"amount\":5} trailing").status());
        assertEquals(400, call("POST", deposits, "{}").status());
        assertEquals(400, call("POST", deposits, "{\"amount\":-5}").status());
        assertEquals(400, call("POST", deposits, "{\"amount\":1.234}").status());
        assertEquals(400, call("POST", "/api/customers", "{\"taxNumber\":\"\",\"name\":\"x\"}").status());
    }

    @Test
    void wrong_method_returns_405_with_allow_header() throws Exception {
        var reply = call("DELETE", "/api/customers", null);

        assertEquals(405, reply.status());
        assertEquals("GET, POST", reply.allow());
    }

    @Test
    void oversized_bodies_are_rejected_with_413() throws Exception {
        var account = newCustomerWithAccount("0");
        var huge = "{\"amount\":1,\"pad\":\"" + "x".repeat(70_000) + "\"}";

        assertEquals(413, call("POST", "/api/accounts/" + account + "/deposits", huge).status());
    }

    @Test
    void concurrent_withdrawals_over_http_never_overdraw() throws Exception {
        var account = newCustomerWithAccount("100");
        var statuses = new ArrayList<Integer>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 40; i++) {
                futures.add(executor.submit(() ->
                        call("POST", "/api/accounts/" + account + "/withdrawals", "{\"amount\":30}").status()));
            }
            for (var f : futures) {
                statuses.add(f.get());
            }
        }

        assertEquals(3, statuses.stream().filter(s -> s == 201).count());
        assertEquals(37, statuses.stream().filter(s -> s == 422).count());
        assertEquals(0, BigDecimal.TEN.compareTo(call("GET", "/api/accounts/" + account, null).body().get("balance").decimalValue()));
    }

    @Test
    void query_parameters_are_url_decoded() throws Exception {
        var tax = uniqueTax();
        call("POST", "/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"Jose da Silva\"}");

        var reply = call("GET", "/api/customers?name=da%20silva&flag&&x=1", null);

        assertEquals(200, reply.status());
        List<String> taxes = new ArrayList<>();
        reply.body().forEach(n -> taxes.add(n.get("taxNumber").asText()));
        assertTrue(taxes.contains(tax));
    }
}
