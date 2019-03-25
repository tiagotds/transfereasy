package br.com.tiagotds.transfereasy.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CustomerAccountsDto extends CustomerDto {

	private List<AccountDto> accounts;
	
	public CustomerAccountsDto() {
		accounts = new ArrayList<>();
	}

	public List<AccountDto> getAccounts() {
		return accounts;
	}

	public void setAccounts(List<AccountDto> accounts) {
		this.accounts = accounts;
	}
	
	
}
