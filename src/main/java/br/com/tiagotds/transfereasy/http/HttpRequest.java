package br.com.tiagotds.transfereasy.http;

import java.util.Map;

/** Framework-free view of an incoming request, as seen by route handlers. */
public record HttpRequest(String method, String path, Map<String, String> query, byte[] body,
                          Map<String, String> pathParams) {

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    public String queryParam(String name) {
        return query.get(name);
    }
}
