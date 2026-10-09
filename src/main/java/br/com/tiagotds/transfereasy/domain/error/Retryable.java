package br.com.tiagotds.transfereasy.domain.error;

/**
 * Marks a failure caused only by a concurrent competitor: running the same operation again, in a fresh
 * transaction, may succeed. Business refusals (insufficient funds, not found...) are never retryable.
 */
public interface Retryable {
}
