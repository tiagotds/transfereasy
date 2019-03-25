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
import br.com.tiagotds.transfereasy.api.dto.CashInOutDto;
import br.com.tiagotds.transfereasy.api.dto.OpenAccountDto;
import br.com.tiagotds.transfereasy.api.dto.TransferDto;
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
	@Path("/cashIn")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response cashIn(@Valid CashInOutDto dto) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		int error = 500;
		boolean success = false;
		try {
			Account account = service.getAccountByNumber(dto.getAccountNumber());
			if (account != null) {
				success = service.cashIn(account, dto.getAmmount());
				if (success) {
					response.setData(AccountMapper.accountToFullDto(account));
					return Response.ok(response).build();
				}
			}
		} catch (TransfereasyException ex) {
			if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
				error = 404;
			} else if (ex.getType().equals(ExceptionType.INVALID)) {
				error = 400;
			}
			response.getErrors().add(ex.getMessage());
		}
		return Response.status(error).entity(response).build();
	}

	@POST
	@Path("/cashOut")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response cashOut(@Valid CashInOutDto dto) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		int error = 500;
		boolean success = false;
		try {
			Account account = service.getAccountByNumber(dto.getAccountNumber());
			if (account != null) {
				success = service.cashOut(account, dto.getAmmount());
				if (success) {
					response.setData(AccountMapper.accountToFullDto(account));
					return Response.ok(response).build();
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

	@POST
	@Path("/transfer")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response Transfer(@Valid TransferDto dto) {
		ResponseBody<AccountFullDto> response = new ResponseBody<>();
		int error = 500;
		boolean success = false;
		try {
			Account from = null;
			Account to = null;
			try {
				from = service.getAccountByNumber(dto.getFromAccount());
			} catch (TransfereasyException ex) {
				if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
					throw new TransfereasyException("Origin account not found.", ExceptionType.NOT_FOUND);
				}
				throw ex;
			}
			if (from != null) {
				try {
					to = service.getAccountByNumber(dto.getToAccount());
				} catch (TransfereasyException ex) {
					if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
						throw new TransfereasyException("Destination account not found.", ExceptionType.NOT_FOUND);
					}
					throw ex;
				}
				if (to != null) {

					if (from.equals(to)) {
						throw new TransfereasyException("Origin and Destination accounts cannot be the same.",
								ExceptionType.INVALID);
					}

					success = service.transfer(from, to, dto.getAmmount());
					if (success) {
						response.setData(AccountMapper.accountToFullDto(from));
						return Response.ok(response).build();
					}
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
