package br.com.tiagotds.transfereasy.api.dto;

public class AccountDto implements DefaultDto {

	private String number;
	private double balance;

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public double getBalance() {
		return balance;
	}

	public void setBalance(double balance) {
		this.balance = balance;
	}

}
