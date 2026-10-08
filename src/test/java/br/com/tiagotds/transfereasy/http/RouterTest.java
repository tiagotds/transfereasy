package br.com.tiagotds.transfereasy.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouterTest {

    private final Router router = new Router()
            .add("GET", "/things", r -> HttpResponse.ok("list"))
            .add("POST", "/things", r -> HttpResponse.created("new"))
            .add("GET", "/things/{id}", r -> HttpResponse.ok(r.pathParam("id")))
            .add("GET", "/things/{id}/parts/{part}", r -> HttpResponse.ok("part"))
            .add("GET", "/", r -> HttpResponse.ok("root"));

    @Test
    void exact_paths_match() {
        assertInstanceOf(Router.Match.Found.class, router.match("GET", "/things"));
    }

    @Test
    void path_variables_are_extracted() {
        var found = assertInstanceOf(Router.Match.Found.class, router.match("GET", "/things/42/parts/a"));
        assertEquals(Map.of("id", "42", "part", "a"), found.params());
    }

    @Test
    void a_trailing_slash_is_tolerated_and_the_root_matches() {
        assertInstanceOf(Router.Match.Found.class, router.match("GET", "/things/"));
        assertInstanceOf(Router.Match.Found.class, router.match("GET", "/"));
    }

    @Test
    void a_known_path_with_another_method_is_405_listing_allowed_methods() {
        var result = assertInstanceOf(Router.Match.MethodNotAllowed.class, router.match("PUT", "/things"));
        assertEquals(Set.of("GET", "POST"), result.allowed());
    }

    @Test
    void unknown_paths_are_not_found() {
        assertInstanceOf(Router.Match.NotFound.class, router.match("GET", "/other"));
        assertInstanceOf(Router.Match.NotFound.class, router.match("GET", "/things/1/parts"));
        assertInstanceOf(Router.Match.NotFound.class, router.match("GET", "/things/1/x/a"));
    }

    @Test
    void handlers_receive_the_request() {
        var found = assertInstanceOf(Router.Match.Found.class, router.match("GET", "/things/7"));
        var request = new HttpRequest("GET", "/things/7", Map.of("q", "1"), new byte[0], found.params());

        var response = found.handler().apply(request);

        assertEquals(200, response.status());
        assertEquals("7", response.body());
        assertEquals("1", request.queryParam("q"));
    }
}
