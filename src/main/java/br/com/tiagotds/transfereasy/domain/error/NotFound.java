package br.com.tiagotds.transfereasy.domain.error;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;

public final class NotFound extends DomainException {

    private NotFound(String message) {
        super(message);
    }

    public static NotFound account(AccountNumber number) {
        return new NotFound("Account '" + number + "' not found.");
    }

    public static NotFound destinationAccount() {
        return new NotFound("Destination account not found.");
    }

    public static NotFound customer() {
        return new NotFound("Customer not found.");
    }
}
