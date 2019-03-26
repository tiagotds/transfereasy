package br.com.tiagotds.transfereasy.api.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import br.com.tiagotds.transfereasy.api.dto.OpenAccountDto;
import br.com.tiagotds.transfereasy.api.entity.Account;
import br.com.tiagotds.transfereasy.api.entity.Customer;
import br.com.tiagotds.transfereasy.api.entity.Transaction;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException.ExceptionType;
import br.com.tiagotds.transfereasy.api.repository.GenericDao;

public class AccountService {

	private GenericDao dao;
	private CustomerService customerService;

	public AccountService() {
		dao = new GenericDao();
		customerService = new CustomerService();
	}

	public Account getAccountByNumber(String number) throws TransfereasyException {
		List<Account> accounts = dao.findByProperty(Account.class, "number", number);
		if (accounts != null && !accounts.isEmpty()) {
			return accounts.get(0);
		} else {
			throw new TransfereasyException("Account not found", ExceptionType.NOT_FOUND);
		}
	}

	public Account newAccount(OpenAccountDto dto) throws TransfereasyException {
		Account account = new Account();
		try {
			Customer customer = customerService.getCustomerByTaxNumber(dto.getTaxNumber());
			if (customer != null) {
				account.setCustomer(customer);
				account.setNumber(generateAccountNumber());
				customer.getAccounts().add(account);
				try {
					dao.beginTransaction();
					Customer saved = dao.merge(customer);
					if (saved.equals(customer)) {
						dao.commitTransaction();
					} else {
						throw new Exception();
					}
				} catch (Exception ex) {
					dao.rollbackTransaction();
					throw new TransfereasyException("Error trying to create a new account.", ExceptionType.UNDEFINED);
				}

			}
		} catch (TransfereasyException ex) {
			throw ex;
		}
		return account;
	}

	private boolean cashIn(Account account, double ammount, boolean isTransfer, String description)
			throws TransfereasyException {
		boolean result = false;

		if (ammount <= 0) {
			throw new TransfereasyException("Invalid ammount.", ExceptionType.INVALID);
		}

		Transaction transaction = new Transaction();
		transaction.setAccount(account);
		transaction.setAmmount(ammount);
		transaction.setDateTime(ZonedDateTime.now());
		transaction.setDescription(description);
		account.getTransactions().add(transaction);
		try {
			dao.beginTransaction(isTransfer);
			Account saved = dao.merge(account);
			result = saved.equals(account);
			dao.commitTransaction(isTransfer);
		} catch (Exception ex) {
			dao.rollbackTransaction(isTransfer);
			throw new TransfereasyException(ex.getMessage(), ExceptionType.UNDEFINED);
		}
		return result;
	}

	private boolean cashOut(Account account, double ammount, boolean isTransfer, String description)
			throws TransfereasyException {
		boolean result = false;

		if (ammount <= 0) {
			throw new TransfereasyException("Invalid ammount.", ExceptionType.INVALID);
		}

		try {
			dao.beginTransaction(isTransfer);
			if (ammount > account.getBalance()) {
				throw new TransfereasyException("There's no balance enough to do this transaction.",
						ExceptionType.NO_BALANCE_ENOUGH);
			}
			Transaction transaction = new Transaction();
			transaction.setAccount(account);
			transaction.setAmmount(-ammount);
			transaction.setDateTime(ZonedDateTime.now());
			transaction.setDescription(description);
			account.getTransactions().add(transaction);
			Account saved = dao.merge(account);
			result = saved.equals(account);
			dao.commitTransaction(isTransfer);
		} catch (Exception ex) {
			dao.rollbackTransaction(isTransfer);
			if (ex instanceof TransfereasyException) {
				throw (TransfereasyException) ex;
			} else {
				throw new TransfereasyException(ex.getMessage(), ExceptionType.UNDEFINED);
			}
		}
		return result;
	}

	public boolean cashIn(Account account, double ammount) throws TransfereasyException {
		return cashIn(account, ammount, false, "Cash in");
	}

	public boolean cashOut(Account account, double ammount) throws TransfereasyException {
		return cashOut(account, ammount, false, "Cash out");
	}

	public boolean transfer(Account from, Account to, double ammount) throws TransfereasyException {
		boolean result = false;
		try {
			dao.beginTransaction();

			result = cashOut(from, ammount, true,
					"Transfer to: " + to.getCustomer().getName() + " Account: " + to.getNumber())
					&& cashIn(to, ammount, true,
							"Transfer from: " + from.getCustomer().getName() + " Account: " + from.getNumber());
			dao.commitTransaction();
		} catch (Exception ex) {
			dao.rollbackTransaction();
			if (ex instanceof TransfereasyException) {
				throw (TransfereasyException) ex;
			} else {
				throw new TransfereasyException(ex.getMessage(), ExceptionType.UNDEFINED);
			}
		}
		return result;
	}

	private String generateAccountNumber() {
		UUID uuid = UUID.randomUUID();
		return uuid.toString().replace("-", "");
	}
}
