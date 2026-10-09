package br.com.tiagotds.transfereasy.infrastructure.http;

/** Error payload returned to clients: a stable machine code plus a human message. */
public record ErrorBody(String code, String message) {
}
