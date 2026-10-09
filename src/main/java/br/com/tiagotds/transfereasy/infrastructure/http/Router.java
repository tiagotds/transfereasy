package br.com.tiagotds.transfereasy.infrastructure.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Minimal method + path-template router ({@code /accounts/{number}/deposits}). */
public final class Router {

    private record Route(String method, String[] segments, HttpRoute handler) {
    }

    public sealed interface Match {
        record Found(HttpRoute handler, Map<String, String> params) implements Match {
        }

        record MethodNotAllowed(Set<String> allowed) implements Match {
        }

        record NotFound() implements Match {
        }
    }

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String template, HttpRoute handler) {
        routes.add(new Route(method, split(template), handler));
        return this;
    }

    public Match match(String method, String path) {
        var actual = split(path);
        var allowed = new TreeSet<String>();
        for (var route : routes) {
            var params = extract(route.segments(), actual);
            if (params == null) {
                continue;
            }
            if (route.method().equals(method)) {
                return new Match.Found(route.handler(), params);
            }
            allowed.add(route.method());
        }
        return allowed.isEmpty() ? new Match.NotFound() : new Match.MethodNotAllowed(allowed);
    }

    private static Map<String, String> extract(String[] template, String[] actual) {
        if (template.length != actual.length) {
            return null;
        }
        var params = new HashMap<String, String>();
        for (int i = 0; i < template.length; i++) {
            var t = template[i];
            if (t.startsWith("{") && t.endsWith("}")) {
                params.put(t.substring(1, t.length() - 1), actual[i]);
            } else if (!t.equals(actual[i])) {
                return null;
            }
        }
        return params;
    }

    private static String[] split(String path) {
        var trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }
}
