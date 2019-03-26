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
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;

import br.com.tiagotds.transfereasy.api.dto.AccountFullDto;
import br.com.tiagotds.transfereasy.api.dto.BankStatementDto;
import br.com.tiagotds.transfereasy.api.dto.OpenAccountDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto.OperationType;
import br.com.tiagotds.transfereasy.api.entity.Account;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException.ExceptionType;
import br.com.tiagotds.transfereasy.api.mapper.AccountMapper;
import br.com.tiagotds.transfereasy.api.service.AccountService;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@Path("/accounts")
public class AccountController {

	private AccountService service;

	public AccountController() {
		service = new AccountService();
	}

	@GET
	@Path("/{number}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAccountByNumber(@PathParam("number") String number) throws TransfereasyException {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		AccountFullDto account = AccountMapper.accountToFullDto(service.getAccountByNumber(number));
		response.setData(account);
		return Response.ok(response).build();
	}

	@GET
	@Path("/{number}/bankStatement")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getBankStatement(@PathParam("number") String number) throws TransfereasyException {
		ResponseBody<BankStatementDto> response = new ResponseBody<>();
		BankStatementDto dto = AccountMapper.accountToBankStatement(service.getAccountByNumber(number));
		response.setData(dto);
		return Response.ok(response).build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response newAccount(@Valid OpenAccountDto dto) throws TransfereasyException, UnrecognizedPropertyException {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		Account account = service.newAccount(dto);
		response.setData(AccountMapper.accountToFullDto(account));
		return Response.status(Status.CREATED).entity(response).build();
	}

	@POST
	@Path("/{number}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response cashIn(@PathParam("number") String number, @Valid OperationDto dto)
			throws TransfereasyException, UnrecognizedPropertyException {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		Account account = service.getAccountByNumber(number);
		if (account != null) {
			if (dto.getType().equals(OperationType.IN)) {
				service.cashIn(account, dto.getAmmount());
			} else if (dto.getType().equals(OperationType.OUT)) {
				service.cashOut(account, dto.getAmmount());
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
					service.transfer(account, toAccount, dto.getAmmount());
				}
			}
		}
		response.setData(AccountMapper.accountToFullDto(account));
		return Response.status(Status.CREATED).entity(response).build();
	}
}
