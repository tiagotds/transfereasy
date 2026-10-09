package br.com.tiagotds.transfereasy.domain.error;

public final class InvalidInput extends DomainException {

    public InvalidInput(String message) {
        super(message);
    }

    public static InvalidInput required(String field) {
        return new InvalidInput("Field '" + field + "' is required.");
    }
}
