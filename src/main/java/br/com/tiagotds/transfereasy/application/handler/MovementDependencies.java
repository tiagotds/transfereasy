package br.com.tiagotds.transfereasy.application.handler;

import br.com.tiagotds.transfereasy.domain.model.AmountPolicy;
import br.com.tiagotds.transfereasy.domain.port.AccountRepository;
import br.com.tiagotds.transfereasy.domain.port.LedgerRepository;
import br.com.tiagotds.transfereasy.infrastructure.persistence.LockingStrategy;
import java.time.Clock;

/** What every money-moving handler needs, grouped once instead of repeated in each constructor. */
public record MovementDependencies(AccountRepository accounts, LedgerRepository ledger, AmountPolicy amounts,
                                  LockingStrategy locking, Clock clock) {
}
