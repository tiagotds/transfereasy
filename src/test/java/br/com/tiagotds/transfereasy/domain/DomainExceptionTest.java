package br.com.tiagotds.transfereasy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.tiagotds.transfereasy.domain.DomainException.Code;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void each_factory_sets_its_code_and_message() {
        assertEquals(Code.INVALID, DomainException.invalid("a").code());
        assertEquals(Code.NOT_FOUND, DomainException.notFound("b").code());
        assertEquals(Code.CONFLICT, DomainException.conflict("c").code());
        assertEquals(Code.INSUFFICIENT_FUNDS, DomainException.insufficientFunds("d").code());
        assertEquals("d", DomainException.insufficientFunds("d").getMessage());
    }
}
