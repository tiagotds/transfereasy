package br.com.tiagotds.transfereasy.api.service;

import java.util.List;

import br.com.tiagotds.transfereasy.api.dto.CustomerDto;
import br.com.tiagotds.transfereasy.api.entity.Customer;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException.ExceptionType;
import br.com.tiagotds.transfereasy.api.repository.GenericDao;
import br.com.tiagotds.transfereasy.api.repository.GenericDao.MatchMode;

public class CustomerService {

	private GenericDao dao;

	public CustomerService() {
		dao = new GenericDao();
	}

	public Customer getCustomerByTaxNumber(String taxNumber) throws TransfereasyException {
		List<Customer> customers = dao.findByProperty(Customer.class, "taxNumber", taxNumber);
		if (customers != null && !customers.isEmpty()) {
			return customers.get(0);
		} else {
			throw new TransfereasyException("Customer not found", ExceptionType.NOT_FOUND);
		}
	}

	public List<Customer> getCustomerByName(String name) throws TransfereasyException {
		List<Customer> customers = dao.findByProperty(Customer.class, "name", name, MatchMode.ANYWHERE);
		if (customers != null && !customers.isEmpty()) {
			return customers;
		} else {
			throw new TransfereasyException("Customers not found", ExceptionType.NOT_FOUND);
		}
	}

	public List<Customer> getAllCustomers() throws TransfereasyException {
		List<Customer> customers = dao.findAll(Customer.class);
		if (customers != null && !customers.isEmpty()) {
			return customers;
		} else {
			throw new TransfereasyException("Customers not found", ExceptionType.NOT_FOUND);
		}
	}

	public Customer newCustomer(CustomerDto dto) throws TransfereasyException {
		Customer customer = null;
		try {
			customer = this.getCustomerByTaxNumber(dto.getTaxNumber());
			if (customer != null) {
				if (dto.getName().equals(customer.getName())) {
					throw new TransfereasyException("Customer already exists", ExceptionType.ALREADY_EXISTS);
				} else {
					throw new TransfereasyException("There's another customer with the same Tax Number.",
							ExceptionType.ALREADY_EXISTS);
				}
			}
		} catch (TransfereasyException ex) {
			if (ex.getType().equals(ExceptionType.NOT_FOUND)) {
				try {
					dao.beginTransaction();
					customer = new Customer();
					customer.setName(dto.getName());
					customer.setTaxNumber(dto.getTaxNumber());
					Integer pk = dao.save(customer);
					if (pk > 0) {
						dao.commitTransaction();
					} else {
						throw new Exception();
					}
				} catch (Exception e) {
					dao.rollbackTransaction();
					throw new TransfereasyException("Error trying to create a new customer.",
							ExceptionType.UNDEFINED);
				}
			} else {
				throw ex;
			}
		}
		return customer;
	}
}
