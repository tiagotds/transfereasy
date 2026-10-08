package br.com.tiagotds.transfereasy.domain;

/** Business-rule violation. The HTTP layer maps each {@link Code} to a status; nothing else leaks out. */
public final class DomainException extends RuntimeException {

    public enum Code { INVALID, NOT_FOUND, CONFLICT, INSUFFICIENT_FUNDS }

    private final Code code;

    private DomainException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static DomainException invalid(String message) {
        return new DomainException(Code.INVALID, message);
    }

    public static DomainException notFound(String message) {
        return new DomainException(Code.NOT_FOUND, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException(Code.CONFLICT, message);
    }

    public static DomainException insufficientFunds(String message) {
        return new DomainException(Code.INSUFFICIENT_FUNDS, message);
    }
}
