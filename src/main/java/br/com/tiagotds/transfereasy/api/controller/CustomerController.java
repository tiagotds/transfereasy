package br.com.tiagotds.transfereasy.api.controller;

import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import br.com.tiagotds.transfereasy.api.dto.CustomerAccountsDto;
import br.com.tiagotds.transfereasy.api.dto.CustomerDto;
import br.com.tiagotds.transfereasy.api.entity.Customer;
import br.com.tiagotds.transfereasy.api.mapper.CustomerMapper;
import br.com.tiagotds.transfereasy.api.service.CustomerService;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;
import br.com.tiagotds.transfereasy.api.util.TransfereasyException;
import br.com.tiagotds.transfereasy.api.util.TransfereasyException.ExceptionType;

@Path("/customers")
public class CustomerController {

	private CustomerService customerService;

	public CustomerController() {
		customerService = new CustomerService();
	}

	@GET
	@Path("/all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllCustomers() {
		ResponseBody<List<CustomerDto>> response = new ResponseBody<>();
		try {
			List<CustomerDto> customers = CustomerMapper.customersToDtos(customerService.getAllCustomers());
			response.setData(customers);
			return Response.ok(response).build();
		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@GET
	@Path("/byName/{name}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomersByName(@PathParam("name") String name) {
		ResponseBody<List<CustomerDto>> response = new ResponseBody<>();
		try {
			List<CustomerDto> customers = CustomerMapper.customersToDtos(customerService.getCustomerByName(name));
			response.setData(customers);
			return Response.ok(response).build();
		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@GET
	@Path("/{taxNumber}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomerByTaxNumber(@PathParam("taxNumber") String taxNumber) {
		ResponseBody<CustomerDto> response = new ResponseBody<>();
		try {
			CustomerDto customer = CustomerMapper.customerToDto(customerService.getCustomerByTaxNumber(taxNumber));
			response.setData(customer);
			return Response.ok(response).build();
		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@GET
	@Path("/{taxNumber}/accounts")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCustomerAccounts(@PathParam("taxNumber") String taxNumber) {
		ResponseBody<CustomerAccountsDto> response = new ResponseBody<>();
		try {
			CustomerAccountsDto customer = CustomerMapper
					.customerToAccountsDto(customerService.getCustomerByTaxNumber(taxNumber));
			response.setData(customer);
			return Response.ok(response).build();
		} catch (TransfereasyException ex) {
			response.getErrors().add(ex.getMessage());
			return Response.status(404).entity(response).build();
		}
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addCustomer(@Valid CustomerDto dto) {
		ResponseBody<CustomerDto> response = new ResponseBody<>();
		int error = 500;
		try {
			Customer customer = customerService.newCustomer(dto);
			if (customer != null) {
				response.setData(dto);
				return Response.status(201).entity(response).build();
			}

		} catch (TransfereasyException ex) {
			if (ex.getType().equals(ExceptionType.ALREADY_EXISTS)) {
				error = 400;
			}
			response.getErrors().add(ex.getMessage());
		}
		return Response.status(error).entity(response).build();
	}

}
