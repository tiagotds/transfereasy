package br.com.tiagotds.transfereasy.infrastructure.http;

import java.util.Map;

/** Framework-free view of an incoming request, as seen by route handlers. */
public record HttpRequest(String method, String path, Map<String, String> query, byte[] body,
                          Map<String, String> pathParams, Map<String, String> headers) {

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public String queryParam(String name) {
        return query.get(name);
    }

    /** First value of the header, matched case-insensitively as HTTP requires; {@code null} when absent. */
    public String header(String name) {
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }
}
