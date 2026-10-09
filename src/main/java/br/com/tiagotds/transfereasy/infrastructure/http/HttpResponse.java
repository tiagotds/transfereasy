package br.com.tiagotds.transfereasy.infrastructure.http;

import java.util.HashMap;
import java.util.Map;

/** Status, JSON body and extra headers. Immutable. */
public record HttpResponse(int status, Object body, Map<String, String> headers) {

    public HttpResponse {
        headers = Map.copyOf(headers);
    }

    public static HttpResponse ok(Object body) {
        return new HttpResponse(200, body, Map.of());
    }

    public static HttpResponse created(Object body) {
        return new HttpResponse(201, body, Map.of());
    }

    public HttpResponse withHeader(String name, String value) {
        var copy = new HashMap<>(headers);
        copy.put(name, value);
        return new HttpResponse(status, body, copy);
    }
}
