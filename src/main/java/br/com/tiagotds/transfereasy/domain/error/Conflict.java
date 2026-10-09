package br.com.tiagotds.transfereasy.domain.error;

import br.com.tiagotds.transfereasy.domain.model.TaxNumber;

/** The request is valid but clashes with the current state (e.g. a duplicate unique key). */
public final class Conflict extends DomainException {

    private Conflict(String message) {
        super(message);
    }

    public static Conflict duplicateCustomer(TaxNumber taxNumber) {
        return new Conflict("A customer with tax number '" + taxNumber + "' already exists.");
    }
}
