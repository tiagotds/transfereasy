package br.com.tiagotds.transfereasy.api.dto;

import java.util.ArrayList;
import java.util.List;

public class BankStatementDto extends AccountFullDto {

	private List<TransactionDto> bankStatement;

	public BankStatementDto() {
		bankStatement = new ArrayList<>();
	}

	public List<TransactionDto> getBankStatement() {
		return bankStatement;
	}

	public void setBankStatement(List<TransactionDto> bankStatement) {
		this.bankStatement = bankStatement;
	}

}
