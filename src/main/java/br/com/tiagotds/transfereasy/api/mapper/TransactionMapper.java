package br.com.tiagotds.transfereasy.api.mapper;

import java.util.ArrayList;
import java.util.List;

import br.com.tiagotds.transfereasy.api.dto.TransactionDto;
import br.com.tiagotds.transfereasy.api.entity.Transaction;

public class TransactionMapper {

	public static TransactionDto transactionToDto(Transaction transaction) {
		TransactionDto dto = new TransactionDto();
		dto.setAmmount(transaction.getAmmount());
		dto.setDateTime(transaction.getDateTime().toString());
		dto.setDescription(transaction.getDescription());
		return dto;
	}

	public static List<TransactionDto> transactionsToDtos(List<Transaction> transactions) {
		List<TransactionDto> dtos = new ArrayList<>();
		for (Transaction transaction : transactions) {
			dtos.add(transactionToDto(transaction));
		}
		return dtos;
	}
}
