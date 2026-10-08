package br.com.tiagotds.transfereasy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

/** Container health probe: exits 0 when /health answers 200. Avoids needing curl in the runtime image. */
public final class HealthCheck {

    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        var port = System.getenv().getOrDefault("PORT", "8080");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build();
        var status = HttpClient.newHttpClient().send(request, BodyHandlers.discarding()).statusCode();
        System.exit(status == 200 ? 0 : 1);
    }
}
