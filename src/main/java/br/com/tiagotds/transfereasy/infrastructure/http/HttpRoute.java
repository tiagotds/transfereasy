package br.com.tiagotds.transfereasy.infrastructure.http;

@FunctionalInterface
public interface HttpRoute {

    HttpResponse handle(HttpRequest request);
}
