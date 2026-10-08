package br.com.tiagotds.transfereasy.http;

/** Error payload returned to clients: a stable machine code plus a human message. */
public record ApiError(String code, String message) {
}
