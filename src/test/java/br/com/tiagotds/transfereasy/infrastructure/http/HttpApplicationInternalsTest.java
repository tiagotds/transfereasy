package br.com.tiagotds.transfereasy.infrastructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.tiagotds.transfereasy.infrastructure.config.HttpSettings;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;

class HttpApplicationInternalsTest {

    @Test
    void parse_query_handles_empty_flags_duplicates_and_encoding() {
        var query = HttpApplication.parseQuery("a=1&b=&flag&a=2&c=x%26y&&");

        assertEquals("1", query.get("a"));
        assertEquals("", query.get("b"));
        assertEquals("", query.get("flag"));
        assertEquals("x&y", query.get("c"));
    }

    @Test
    void parse_query_of_null_or_empty_is_empty() {
        assertTrue(HttpApplication.parseQuery(null).isEmpty());
        assertTrue(HttpApplication.parseQuery("").isEmpty());
    }

    @Test
    void when_the_server_is_saturated_it_sheds_load_with_503_but_health_still_answers() throws Exception {
        var busy = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var router = new Router()
                .add("GET", "/health", r -> HttpResponse.ok("UP"))
                .add("GET", "/slow", r -> {
                    busy.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return HttpResponse.ok("done");
                });
        var settings = new HttpSettings(0, 1024, 1, Duration.ofMillis(20), Duration.ofSeconds(2));
        try (var app = new HttpApplication(settings, router)) {
            app.start();
            var client = HttpClient.newHttpClient();
            var base = "http://localhost:" + app.port();
            var slow = client.sendAsync(HttpRequest.newBuilder(URI.create(base + "/slow")).build(),
                    BodyHandlers.ofString());
            assertTrue(busy.await(5, TimeUnit.SECONDS));

            var shed = client.send(HttpRequest.newBuilder(URI.create(base + "/slow")).build(), BodyHandlers.ofString());
            var health = client.send(HttpRequest.newBuilder(URI.create(base + "/health")).build(),
                    BodyHandlers.ofString());
            release.countDown();

            assertEquals(503, shed.statusCode());
            assertTrue(shed.body().contains("OVERLOADED"));
            assertEquals("2", shed.headers().firstValue("Retry-After").orElseThrow());
            assertEquals(200, health.statusCode(), "health checks bypass the bulkhead");
            assertEquals(200, slow.get(5, TimeUnit.SECONDS).statusCode());
        }
    }

    @Test
    void an_unexpected_exception_in_a_handler_becomes_a_generic_500_without_leaking_details() throws Exception {
        var router = new Router()
                .add("GET", "/boom", r -> {
                    throw new IllegalStateException("secret internal detail");
                })
                .add("GET", "/fatal", r -> {
                    throw new StackOverflowError("secret fatal detail");
                })
                .add("GET", "/echo", r -> HttpResponse.ok(r.header("X-Probe")).withHeader("X-Answer", "42"));
        try (var app = new HttpApplication(new HttpSettings(0, 1024, 8, Duration.ZERO, Duration.ofSeconds(1)), router)) {
            app.start();
            var client = HttpClient.newHttpClient();
            for (var path : new String[]{"/boom", "/fatal"}) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + app.port() + path)).build(), BodyHandlers.ofString());

                assertEquals(500, response.statusCode());
                assertTrue(response.body().contains("INTERNAL_ERROR"));
                assertTrue(!response.body().contains("secret"), "internal details must not leak");
            }
            var echo = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/echo"))
                    .header("x-probe", "hello").build(), BodyHandlers.ofString());
            assertEquals("\"hello\"", echo.body(), "request headers reach handlers");
            assertEquals("42", echo.headers().firstValue("X-Answer").orElseThrow(), "handler headers are sent");
            // the server must still be alive after the failures
            var health = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + app.port() + "/boom")).build(), BodyHandlers.ofString());
            assertEquals(500, health.statusCode());
        }
    }
}
