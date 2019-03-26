package br.com.tiagotds.transfereasy.api.controller;

import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;

import br.com.tiagotds.transfereasy.api.dto.CustomerAccountsDto;
import br.com.tiagotds.transfereasy.api.dto.CustomerDto;
import br.com.tiagotds.transfereasy.api.entity.Customer;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyException;
import br.com.tiagotds.transfereasy.api.mapper.CustomerMapper;
import br.com.tiagotds.transfereasy.api.service.CustomerService;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@Path("/customers")
public class CustomerController {

	private CustomerService customerService;

	public CustomerController() {
		customerService = new CustomerService();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomers(@QueryParam("name") String name) throws TransfereasyException {
		ResponseBody<List<CustomerDto>> response = new ResponseBody<>();
		List<CustomerDto> customers;
		if (name == null || name.isEmpty()) {
			customers = CustomerMapper.customersToDtos(customerService.getAllCustomers());
		} else {
			customers = CustomerMapper.customersToDtos(customerService.getCustomerByName(name));
		}
		response.setData(customers);
		return Response.ok(response).build();
	}

	@GET
	@Path("/{taxNumber}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomerByTaxNumber(@PathParam("taxNumber") String taxNumber) throws TransfereasyException {
		ResponseBody<CustomerDto> response = new ResponseBody<>();
		response.setData(CustomerMapper.customerToDto(customerService.getCustomerByTaxNumber(taxNumber)));
		return Response.ok(response).build();
	}

	@GET
	@Path("/{taxNumber}/accounts")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomerAccounts(@PathParam("taxNumber") String taxNumber) throws TransfereasyException {
		ResponseBody<CustomerAccountsDto> response = new ResponseBody<>();
		response.setData(CustomerMapper.customerToAccountsDto(customerService.getCustomerByTaxNumber(taxNumber)));
		return Response.ok(response).build();
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addCustomer(@Valid CustomerDto dto) throws TransfereasyException, UnrecognizedPropertyException {
		ResponseBody<CustomerDto> response = new ResponseBody<>();
		Customer customer = customerService.newCustomer(dto);
		if (customer != null) {
			response.setData(dto);
		}
		return Response.status(Status.CREATED).entity(response).build();

	}

}
