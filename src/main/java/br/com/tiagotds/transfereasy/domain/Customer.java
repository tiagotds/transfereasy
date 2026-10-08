package br.com.tiagotds.transfereasy.domain;

import java.time.OffsetDateTime;

public record Customer(long id, String taxNumber, String name, OffsetDateTime createdAt) {
}
