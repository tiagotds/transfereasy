package br.com.tiagotds.transfereasy.http;

import br.com.tiagotds.transfereasy.domain.Account;
import br.com.tiagotds.transfereasy.domain.Customer;
import br.com.tiagotds.transfereasy.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Request and response payloads. Amounts are exact decimals serialised as plain JSON numbers. */
public final class Dtos {

    private Dtos() {
    }

    // ---- requests
    public record CreateCustomerRequest(String taxNumber, String name) {
    }

    public record OpenAccountRequest(String taxNumber) {
    }

    public record AmountRequest(BigDecimal amount) {
    }

    public record TransferRequest(String toAccountNumber, BigDecimal amount) {
    }

    // ---- responses
    public record CustomerResponse(String taxNumber, String name, OffsetDateTime createdAt) {
        static CustomerResponse of(Customer c) {
            return new CustomerResponse(c.taxNumber(), c.name(), c.createdAt());
        }
    }

    public record AccountResponse(String number, BigDecimal balance, OffsetDateTime createdAt) {
        static AccountResponse of(Account a) {
            return new AccountResponse(a.number(), a.balance(), a.createdAt());
        }
    }

    public record CustomerAccountsResponse(CustomerResponse customer, List<AccountResponse> accounts) {
    }

    public record EntryResponse(long id, String kind, BigDecimal amount, BigDecimal balanceAfter,
                                String counterpartyAccountNumber, String transferId, OffsetDateTime createdAt) {
        static EntryResponse of(LedgerEntry e) {
            return new EntryResponse(e.id(), e.kind().name(), e.amount(), e.balanceAfter(),
                    e.counterpartyAccountNumber(), e.transferId(), e.createdAt());
        }
    }

    public record StatementResponse(AccountResponse account, List<EntryResponse> entries) {
    }

    public record TransferResponse(String transferId, AccountResponse from, AccountResponse to) {
    }
}
