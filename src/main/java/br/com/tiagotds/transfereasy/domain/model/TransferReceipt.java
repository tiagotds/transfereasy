package br.com.tiagotds.transfereasy.domain.model;

public record TransferReceipt(String transferId, Account from, Account to) {
}
