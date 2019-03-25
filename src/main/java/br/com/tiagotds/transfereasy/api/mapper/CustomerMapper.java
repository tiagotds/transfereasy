package br.com.tiagotds.transfereasy.api.mapper;

import java.util.ArrayList;
import java.util.List;

import br.com.tiagotds.transfereasy.api.dto.CustomerAccountsDto;
import br.com.tiagotds.transfereasy.api.dto.CustomerDto;
import br.com.tiagotds.transfereasy.api.entity.Customer;

public class CustomerMapper {

	public static CustomerDto customerToDto(Customer customer) {
		CustomerDto dto = new CustomerDto();
		dto.setName(customer.getName());
		dto.setTaxNumber(customer.getTaxNumber());
		return dto;
	}

	public static List<CustomerDto> customersToDtos(List<Customer> customers) {
		List<CustomerDto> dtos = new ArrayList<>();
		for (Customer customer : customers) {
			dtos.add(customerToDto(customer));
		}
		return dtos;
	}

	public static CustomerAccountsDto customerToAccountsDto(Customer customer) {
		CustomerAccountsDto dto = new CustomerAccountsDto();
		dto.setName(customer.getName());
		dto.setTaxNumber(customer.getTaxNumber());
		dto.setAccounts(AccountMapper.accountsToDtos(customer.getAccounts()));
		return dto;
	}
}
