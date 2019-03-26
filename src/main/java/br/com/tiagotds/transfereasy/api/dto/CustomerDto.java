package br.com.tiagotds.transfereasy.api.dto;

import org.hibernate.validator.constraints.NotEmpty;

public class CustomerDto implements DefaultDto {

	@NotEmpty(message = "Name is required")
	public String name;
	@NotEmpty(message = "Tax number number is required")
	public String taxNumber;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTaxNumber() {
		return taxNumber;
	}

	public void setTaxNumber(String taxNumber) {
		this.taxNumber = taxNumber;
	}

	public boolean equals(CustomerDto o) {
		return this.name.equals(o.getName()) && this.taxNumber.equals(o.getTaxNumber());
	}
}
