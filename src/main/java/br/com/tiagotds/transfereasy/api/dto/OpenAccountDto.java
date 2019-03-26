package br.com.tiagotds.transfereasy.api.dto;

import org.hibernate.validator.constraints.NotEmpty;

public class OpenAccountDto implements DefaultDto {

	@NotEmpty(message = "Tax number is required")
	private String taxNumber;

	public String getTaxNumber() {
		return taxNumber;
	}

	public void setTaxNumber(String taxNumber) {
		this.taxNumber = taxNumber;
	}

}
