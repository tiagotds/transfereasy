package br.com.tiagotds.transfereasy.domain.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.tiagotds.transfereasy.domain.model.AccountNumber;
import br.com.tiagotds.transfereasy.domain.model.TaxNumber;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void factories_describe_the_problem() {
        assertEquals("Account 'abc' not found.", NotFound.account(AccountNumber.of("abc")).getMessage());
        assertEquals("Customer not found.", NotFound.customer().getMessage());
        assertEquals("Destination account not found.", NotFound.destinationAccount().getMessage());
        assertEquals("A customer with tax number '1' already exists.",
                Conflict.duplicateCustomer(TaxNumber.of("1")).getMessage());
        assertEquals("Insufficient funds for this operation.", new InsufficientFunds().getMessage());
        assertEquals("bad", new InvalidInput("bad").getMessage());
    }

    @Test
    void business_outcomes_are_cheap_to_throw_because_they_carry_no_stack_trace() {
        assertEquals(0, new InsufficientFunds().getStackTrace().length);
    }

    @Test
    void the_hierarchy_is_closed_so_every_handler_can_be_exhaustive() {
        DomainException e = new InsufficientFunds();
        var name = switch (e) {
            case InvalidInput i -> "invalid";
            case NotFound n -> "not found";
            case Conflict c -> "conflict";
            case InsufficientFunds f -> "funds";
        };
        assertEquals("funds", name);
    }
}
