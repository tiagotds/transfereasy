package br.com.tiagotds.transfereasy.api.controller;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import br.com.tiagotds.transfereasy.api.dto.AccountFullDto;
import br.com.tiagotds.transfereasy.api.dto.BankStatementDto;
import br.com.tiagotds.transfereasy.api.dto.OpenAccountDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto.OperationType;
import br.com.tiagotds.transfereasy.api.entity.Account;
import br.com.tiagotds.transfereasy.api.mapper.AccountMapper;
import br.com.tiagotds.transfereasy.api.service.AccountService;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;
import br.com.tiagotds.transfereasy.api.util.TransfereasyException;
import br.com.tiagotds.transfereasy.api.util.TransfereasyException.ExceptionType;

@Path("/accounts")
public class AccountController {

	private AccountService service;

	public AccountController() {
		service = new AccountService();
	}

	@GET
	@Path("/{number}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAccountByNumber(@PathParam("number") String number) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		try {
			AccountFullDto account = AccountMapper.accountToFullDto(service.getAccountByNumber(number));
			response.setData(account);
			return Response.ok(response).build();

		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@GET
	@Path("/{number}/bankStatement")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getBankStatement(@PathParam("number") String number) {
		ResponseBody<BankStatementDto> response = new ResponseBody<>();
		try {
			BankStatementDto dto = AccountMapper.accountToBankStatement(service.getAccountByNumber(number));
			response.setData(dto);
			return Response.ok(response).build();

		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response newAccount(@Valid OpenAccountDto dto) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		int error = 500;
		try {
			Account account = service.newAccount(dto);
			if (account != null) {
				response.setData(AccountMapper.accountToFullDto(account));
				return Response.status(201).entity(response).build();
			}
		} catch (TransfereasyException ex) {
			if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
				error = 404;
			}
			response.getErrors().add(ex.getMessage());
		}
		return Response.status(error).entity(response).build();
	}

	@POST
	@Path("/{number}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response cashIn(@PathParam("number") String number, @Valid OperationDto dto) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		int error = 500;
		boolean success = false;
		try {
			Account account = service.getAccountByNumber(number);
			if (account != null) {
				if (dto.getOperationType() == null) {
					throw new TransfereasyException("Operation type invalid", ExceptionType.INVALID);
				}

				if (dto.getOperationType().equals(OperationType.IN)) {
					success = service.cashIn(account, dto.getAmmount());
				} else if (dto.getOperationType().equals(OperationType.OUT)) {
					success = service.cashOut(account, dto.getAmmount());
				} else {
					if (dto.getToAccountNumber() == null || dto.getToAccountNumber().isEmpty()) {
						throw new TransfereasyException("Destination account is missing", ExceptionType.INVALID);
					}
					Account toAccount;
					try {
						toAccount = service.getAccountByNumber(dto.getToAccountNumber());
					} catch (TransfereasyException ex) {
						if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
							throw new TransfereasyException("Destination account not found.", ExceptionType.NOT_FOUND);
						}
						throw ex;
					}
					if (toAccount != null) {

						if (account.equals(toAccount)) {
							throw new TransfereasyException("Origin and Destination accounts cannot be the same.",
									ExceptionType.INVALID);
						}

						success = service.transfer(account, toAccount, dto.getAmmount());
						if (success) {
							response.setData(AccountMapper.accountToFullDto(account));
							return Response.status(201).entity(response).build();
						}
					}
				}
				if (success) {
					response.setData(AccountMapper.accountToFullDto(account));
					return Response.status(201).entity(response).build();
				}
			}
		} catch (TransfereasyException ex) {
			if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
				error = 404;
			} else if (ex.getType().equals(ExceptionType.INVALID)
					|| ex.getType().equals(ExceptionType.NO_BALANCE_ENOUGH)) {
				error = 400;
			}
			response.getErrors().add(ex.getMessage());
		}
		return Response.status(error).entity(response).build();
	}
}
