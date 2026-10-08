package br.com.tiagotds.transfereasy.http;

import br.com.tiagotds.transfereasy.domain.DomainException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** JDK built-in HTTP server running one virtual thread per request. */
public final class HttpApplication implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(HttpApplication.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpServer server;
    private final Router router;

    public HttpApplication(int port, Router router) throws IOException {
        this.router = router;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        if (server.getExecutor() instanceof AutoCloseable executor) {
            try {
                executor.close();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Executor shutdown failed", e);
            }
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var response = dispatch(exchange);
            write(exchange, response);
        }
    }

    private record Reply(int status, Object body, Map<String, String> headers) {
    }

    private Reply dispatch(HttpExchange exchange) {
        try {
            var method = exchange.getRequestMethod();
            var uri = exchange.getRequestURI();
            return switch (router.match(method, uri.getPath())) {
                case Router.Match.NotFound ignored ->
                        error(404, "NOT_FOUND", "No such resource.", Map.of());
                case Router.Match.MethodNotAllowed m ->
                        error(405, "METHOD_NOT_ALLOWED", "Method not allowed.",
                                Map.of("Allow", String.join(", ", m.allowed())));
                case Router.Match.Found found -> {
                    var body = readBody(exchange);
                    if (body == null) {
                        yield error(413, "PAYLOAD_TOO_LARGE", "Request body too large.", Map.of());
                    }
                    var request = new HttpRequest(method, uri.getPath(), parseQuery(uri.getRawQuery()), body,
                            found.params());
                    var result = found.handler().apply(request);
                    yield new Reply(result.status(), result.body(), Map.of());
                }
            };
        } catch (DomainException e) {
            return fromDomain(e);
        } catch (Throwable t) {
            // Includes Errors: a request must always get an answer and must never leak internals.
            LOG.log(Level.SEVERE, "Unhandled error while serving request", t);
            return error(500, "INTERNAL_ERROR", "Unexpected error.", Map.of());
        }
    }

    private static Reply fromDomain(DomainException e) {
        return switch (e.code()) {
            case INVALID -> error(400, "INVALID_REQUEST", e.getMessage(), Map.of());
            case NOT_FOUND -> error(404, "NOT_FOUND", e.getMessage(), Map.of());
            case CONFLICT -> error(409, "CONFLICT", e.getMessage(), Map.of());
            case INSUFFICIENT_FUNDS -> error(422, "INSUFFICIENT_FUNDS", e.getMessage(), Map.of());
        };
    }

    private static Reply error(int status, String code, String message, Map<String, String> headers) {
        return new Reply(status, new ApiError(code, message), headers);
    }

    /** @return the body bytes, or {@code null} when the limit is exceeded. */
    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            var bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            return bytes.length > MAX_BODY_BYTES ? null : bytes;
        }
    }

    static Map<String, String> parseQuery(String raw) {
        var result = new HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (var pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            var key = decode(eq < 0 ? pair : pair.substring(0, eq));
            var value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            result.putIfAbsent(key, value);
        }
        return result;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static void write(HttpExchange exchange, Reply reply) throws IOException {
        var bytes = Json.write(reply.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        reply.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
