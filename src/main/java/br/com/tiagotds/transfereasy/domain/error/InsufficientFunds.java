package br.com.tiagotds.transfereasy.domain.error;

public final class InsufficientFunds extends DomainException {

    public InsufficientFunds() {
        super("Insufficient funds for this operation.");
    }
}
