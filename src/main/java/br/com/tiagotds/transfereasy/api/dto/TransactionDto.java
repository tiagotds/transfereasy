package br.com.tiagotds.transfereasy.api.dto;

import java.time.ZonedDateTime;

public class TransactionDto {

	private String dateTime;
	private String Description;
	private double ammount;

	public String getDateTime() {
		return dateTime;
	}

	public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}

	public String getDescription() {
		return Description;
	}

	public void setDescription(String description) {
		Description = description;
	}

	public double getAmmount() {
		return ammount;
	}

	public void setAmmount(double ammount) {
		this.ammount = ammount;
	}

}
