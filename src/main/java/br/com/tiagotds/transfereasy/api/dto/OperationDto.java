package br.com.tiagotds.transfereasy.api.dto;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.NotNull;

public class OperationDto implements DefaultDto {

	public enum OperationType {
		IN, OUT, TRANSFER
	}

	private String toAccountNumber;
	@NotNull(message = "Invalid ammount.")
	private double ammount;
	@NotNull(message = "Type must be \"IN\", \"OUT\" or \"TRANSFER\"")
	@Enumerated(EnumType.STRING)
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

	public OperationType getType() {
		return type;
	}

	public void setType(OperationType type) {
		this.type = type;
	}

}
