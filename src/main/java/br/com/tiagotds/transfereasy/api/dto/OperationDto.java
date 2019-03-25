package br.com.tiagotds.transfereasy.api.dto;

public class OperationDto {

	public enum OperationType {
		IN, OUT, TRANSFER
	}

	private String toAccountNumber;
	private double ammount;
	private OperationType type;

	public String getToAccountNumber() {
		return toAccountNumber;
	}

	public void setToAccountNumber(String accountNumber) {
		this.toAccountNumber = accountNumber;
	}

	public double getAmmount() {
		return ammount;
	}

	public void setAmmount(double ammount) {
		this.ammount = ammount;
	}

	public OperationType getOperationType() {
		return type;
	}

	public void setOperationType(OperationType type) {
		this.type = type;
	}

}
