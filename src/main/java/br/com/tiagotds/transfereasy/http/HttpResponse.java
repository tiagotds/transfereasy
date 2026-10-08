package br.com.tiagotds.transfereasy.http;

public record HttpResponse(int status, Object body) {

    public static HttpResponse ok(Object body) {
        return new HttpResponse(200, body);
    }

    public static HttpResponse created(Object body) {
        return new HttpResponse(201, body);
    }
}
