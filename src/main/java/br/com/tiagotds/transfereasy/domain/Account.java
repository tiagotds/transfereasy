package br.com.tiagotds.transfereasy.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Account(long id, String number, long customerId, BigDecimal balance, OffsetDateTime createdAt) {
}
