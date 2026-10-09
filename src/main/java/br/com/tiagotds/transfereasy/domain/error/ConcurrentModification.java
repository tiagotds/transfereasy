package br.com.tiagotds.transfereasy.domain.error;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;

/** Someone else changed the aggregate between our read and our write (optimistic lock lost). */
public final class ConcurrentModification extends DomainException implements Retryable {

    private ConcurrentModification(String message) {
        super(message);
    }

    public static ConcurrentModification of(AccountNumber number) {
        return new ConcurrentModification("Account '" + number + "' was modified concurrently, please retry.");
    }
}
