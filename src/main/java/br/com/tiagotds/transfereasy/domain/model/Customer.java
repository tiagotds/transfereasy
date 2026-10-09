package br.com.tiagotds.transfereasy.domain.model;

import java.time.OffsetDateTime;

public record Customer(long id, TaxNumber taxNumber, CustomerName name, OffsetDateTime createdAt) {
}
