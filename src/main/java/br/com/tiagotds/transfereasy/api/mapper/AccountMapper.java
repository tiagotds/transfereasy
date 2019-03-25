package br.com.tiagotds.transfereasy.api.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.tiagotds.transfereasy.api.dto.AccountDto;
import br.com.tiagotds.transfereasy.api.dto.AccountFullDto;
import br.com.tiagotds.transfereasy.api.dto.BankStatementDto;
import br.com.tiagotds.transfereasy.api.entity.Account;

public class AccountMapper {

	public static AccountFullDto accountToFullDto(Account account) {
		AccountFullDto dto = new AccountFullDto();
		dto.setNumber(account.getNumber());
		dto.setBalance(account.getBalance());
		dto.setCustomer(CustomerMapper.customerToDto(account.getCustomer()));
		return dto;
	}

	public static AccountDto accountToDto(Account account) {
		AccountDto dto = new AccountDto();
		dto.setNumber(account.getNumber());
		dto.setBalance(account.getBalance());
		return dto;
	}

	public static List<AccountDto> accountsToDtos(List<Account> accounts) {
		List<AccountDto> dtos = new ArrayList<>();
		for (Account account : accounts) {
			dtos.add(accountToDto(account));
		}
		return dtos;
	}

	public static BankStatementDto accountToBankStatement(Account account) {
		BankStatementDto dto = new BankStatementDto();
		dto.setNumber(account.getNumber());
		dto.setBalance(account.getBalance());
		dto.setCustomer(CustomerMapper.customerToDto(account.getCustomer()));
		Collections.sort(account.getTransactions());
		dto.setBankStatement(TransactionMapper.transactionsToDtos(account.getTransactions()));
		return dto;
	}
}
