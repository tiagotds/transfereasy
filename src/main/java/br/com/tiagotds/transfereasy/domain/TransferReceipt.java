package br.com.tiagotds.transfereasy.domain;

public record TransferReceipt(String transferId, Account from, Account to) {
}
