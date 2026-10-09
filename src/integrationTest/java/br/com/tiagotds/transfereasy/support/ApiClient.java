package br.com.tiagotds.transfereasy.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.atomic.AtomicInteger;

/** A tiny HTTP client for the API, as an external caller would use it. Thread-safe. */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public record Reply(int status, JsonNode body, String header) {

        public BigDecimal balance() {
            return body.get("balance").decimalValue();
        }

        public String code() {
            return body.get("code").asText();
        }
    }

    private final HttpClient client = HttpClient.newHttpClient();
    private final String base;

    public ApiClient(int port) {
        this.base = "http://localhost:" + port;
    }

    public Reply get(String path) throws Exception {
        return send("GET", path, null, null, null);
    }

    public Reply post(String path, String json) throws Exception {
        return send("POST", path, json, null, null);
    }

    public Reply post(String path, String json, String idempotencyKey) throws Exception {
        return send("POST", path, json, idempotencyKey, "Idempotent-Replayed");
    }

    /** @param readHeader name of a response header to capture into {@link Reply#header()} */
    public Reply send(String method, String path, String json, String idempotencyKey, String readHeader)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json))
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        var response = client.send(builder.build(), BodyHandlers.ofString());
        var header = readHeader == null ? null : response.headers().firstValue(readHeader).orElse(null);
        return new Reply(response.statusCode(), JSON.readTree(response.body()), header);
    }

    /** Creates a customer with one account holding {@code balance}; returns the account number. */
    public String accountWithBalance(String balance) throws Exception {
        var tax = "IT-" + SEQUENCE.incrementAndGet();
        expect(201, post("/api/customers", "{\"taxNumber\":\"" + tax + "\",\"name\":\"Person " + tax + "\"}"));
        var number = expect(201, post("/api/accounts", "{\"taxNumber\":\"" + tax + "\"}")).body().get("number").asText();
        if (new BigDecimal(balance).signum() > 0) {
            expect(201, post("/api/accounts/" + number + "/deposits", "{\"amount\":" + balance + "}"));
        }
        return number;
    }

    public BigDecimal balanceOf(String account) throws Exception {
        return expect(200, get("/api/accounts/" + account)).balance();
    }

    private static Reply expect(int status, Reply reply) {
        if (reply.status() != status) {
            throw new AssertionError("expected " + status + " but got " + reply.status() + ": " + reply.body());
        }
        return reply;
    }
}
