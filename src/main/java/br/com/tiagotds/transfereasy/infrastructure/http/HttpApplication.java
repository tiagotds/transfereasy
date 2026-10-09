package br.com.tiagotds.transfereasy.infrastructure.http;

import br.com.tiagotds.transfereasy.infrastructure.config.HttpSettings;
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

    private final HttpServer server;
    private final Router router;
    private final int maxBodyBytes;

    public HttpApplication(HttpSettings settings, Router router) throws IOException {
        this.router = router;
        this.maxBodyBytes = settings.maxBodyBytes();
        this.server = HttpServer.create(new InetSocketAddress(settings.port()), 0);
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
            write(exchange, dispatch(exchange));
        }
    }

    private HttpResponse dispatch(HttpExchange exchange) {
        try {
            var method = exchange.getRequestMethod();
            var uri = exchange.getRequestURI();
            return switch (router.match(method, uri.getPath())) {
                case Router.Match.NotFound ignored -> ErrorMapper.noRoute();
                case Router.Match.MethodNotAllowed m -> ErrorMapper.methodNotAllowed(m.allowed());
                case Router.Match.Found found -> {
                    var body = readBody(exchange);
                    if (body == null) {
                        yield ErrorMapper.payloadTooLarge();
                    }
                    yield found.handler().handle(new HttpRequest(method, uri.getPath(), parseQuery(uri.getRawQuery()),
                            body, found.params(), headers(exchange)));
                }
            };
        } catch (Throwable t) {
            // Includes Errors: a request must always get an answer and must never leak internals.
            return ErrorMapper.toResponse(t);
        }
    }

    /** @return the body bytes, or {@code null} when the limit is exceeded. */
    private byte[] readBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            var bytes = in.readNBytes(maxBodyBytes + 1);
            return bytes.length > maxBodyBytes ? null : bytes;
        }
    }

    private static Map<String, String> headers(HttpExchange exchange) {
        var result = new HashMap<String, String>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                result.put(name, values.getFirst());
            }
        });
        return result;
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

    private static void write(HttpExchange exchange, HttpResponse response) throws IOException {
        var bytes = Json.write(response.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        response.headers().forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
        exchange.sendResponseHeaders(response.status(), bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
