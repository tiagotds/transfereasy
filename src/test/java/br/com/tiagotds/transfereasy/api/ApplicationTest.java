package br.com.tiagotds.transfereasy.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.core.SynchronousExecutionContext;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import br.com.tiagotds.transfereasy.api.controller.AccountController;
import br.com.tiagotds.transfereasy.api.controller.CustomerController;
import br.com.tiagotds.transfereasy.api.dto.AccountFullDto;
import br.com.tiagotds.transfereasy.api.dto.BankStatementDto;
import br.com.tiagotds.transfereasy.api.dto.CustomerAccountsDto;
import br.com.tiagotds.transfereasy.api.dto.CustomerDto;
import br.com.tiagotds.transfereasy.api.dto.OpenAccountDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto;
import br.com.tiagotds.transfereasy.api.dto.OperationDto.OperationType;
import br.com.tiagotds.transfereasy.api.exception.InvalidEntryJsonHandler;
import br.com.tiagotds.transfereasy.api.exception.InvalidJsonEnumHandler;
import br.com.tiagotds.transfereasy.api.exception.InvalidJsonHandler;
import br.com.tiagotds.transfereasy.api.exception.TransfereasyExceptionHandler;
import br.com.tiagotds.transfereasy.api.util.JSONUtils;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@RunWith(JUnit4.class)
public class ApplicationTest {

	private static Dispatcher dispatcher;

	private static CustomerDto firstCustomer;
	private static CustomerDto secondCustomer;

	private static AccountFullDto accountFirstCostumer;
	private static AccountFullDto accountSecondCostumer;

	@BeforeClass
	public static void beforeClass() {
		dispatcher = MockDispatcherFactory.createDispatcher();
		dispatcher.getRegistry().addSingletonResource(new CustomerController());
		dispatcher.getRegistry().addSingletonResource(new AccountController());

		dispatcher.getProviderFactory().registerProvider(TransfereasyExceptionHandler.class);
		dispatcher.getProviderFactory().registerProvider(InvalidEntryJsonHandler.class);
		dispatcher.getProviderFactory().registerProvider(InvalidJsonHandler.class);
		dispatcher.getProviderFactory().registerProvider(InvalidJsonEnumHandler.class);

		firstCustomer = new CustomerDto();
		firstCustomer.setName("TIAGO DONIZETE DOS SANTOS");
		firstCustomer.setTaxNumber("35859319878");

		secondCustomer = new CustomerDto();
		secondCustomer.setName("RICARDO OLIVEIRA DO NASCIMENTO");
		secondCustomer.setTaxNumber("10714380776");
	}

	@Test
	public void customersTest() throws URISyntaxException, JsonParseException, JsonMappingException, IOException {
		MockHttpResponse response = sendAsyncGetRequest("/customers");
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		response = sendAsyncPostRequest("/customers", JSONUtils.convertObjectToJsonString(firstCustomer));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		response = sendAsyncGetRequest("/customers");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		ResponseBody<List<CustomerDto>> bodyAll = JSONUtils.convertJsonToObject(response.getContentAsString(),
				ResponseBody.class);
		assertTrue(bodyAll.getData().size() == 1);

		response = sendAsyncPostRequest("/customers", JSONUtils.convertObjectToJsonString(firstCustomer));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		ResponseBody<CustomerDto> bodyOne = JSONUtils.convertJsonToObject(response.getContentAsString(),
				ResponseBody.class);
		assertTrue(bodyOne.getErrors().contains("Customer already exists"));

		firstCustomer.setName("RICARDO OLIVEIRA DO NASCIMENTO");
		response = sendAsyncPostRequest("/customers", JSONUtils.convertObjectToJsonString(firstCustomer));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyOne = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyOne.getErrors().contains("There's another customer with the same Tax Number."));

		firstCustomer.setName("TIAGO DONIZETE DOS SANTOS");

		response = sendAsyncPostRequest("/customers", JSONUtils.convertObjectToJsonString(secondCustomer));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		response = sendAsyncGetRequest("/customers");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyAll = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAll.getData().size() == 2);

		response = sendAsyncGetRequest("/customers/999999999999");
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		bodyOne = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyOne.getErrors().contains("Customer not found"));

		response = sendAsyncGetRequest("/customers/" + firstCustomer.getTaxNumber());
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyOne = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		CustomerDto dto = JSONUtils.cast(bodyOne.getData(), CustomerDto.class);
		assertTrue(dto.equals(firstCustomer));

		response = sendAsyncGetRequest("/customers?name=FULANO");
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		bodyAll = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAll.getErrors().contains("Customers not found"));

		response = sendAsyncGetRequest("/customers?name=DONIZETE");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyAll = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAll.getData().size() == 1);
	}

	@Test
	public void accountsTest() throws URISyntaxException, JsonParseException, JsonMappingException,
			UnsupportedEncodingException, IOException {
		MockHttpResponse response = sendAsyncGetRequest("/customers/" + firstCustomer.getTaxNumber() + "/accounts");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		ResponseBody<CustomerAccountsDto> bodyCustomerAccount = JSONUtils
				.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		CustomerAccountsDto customerAccounts = JSONUtils.cast(bodyCustomerAccount.getData(), CustomerAccountsDto.class);
		assertTrue(customerAccounts.getAccounts().size() == 0);

		OpenAccountDto openAccount = new OpenAccountDto();
		openAccount.setTaxNumber("99999999999");

		response = sendAsyncPostRequest("/accounts", JSONUtils.convertObjectToJsonString(openAccount));
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		ResponseBody<AccountFullDto> bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(),
				ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Customer not found"));

		openAccount.setTaxNumber(firstCustomer.getTaxNumber());
		response = sendAsyncPostRequest("/accounts", JSONUtils.convertObjectToJsonString(openAccount));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountFirstCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);

		openAccount.setTaxNumber(secondCustomer.getTaxNumber());
		response = sendAsyncPostRequest("/accounts", JSONUtils.convertObjectToJsonString(openAccount));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountSecondCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);

		response = sendAsyncGetRequest("/accounts/9999999999999");
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		response = sendAsyncGetRequest("/accounts/" + accountFirstCostumer.getNumber());
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		response = sendAsyncGetRequest("/customers/" + firstCustomer.getTaxNumber() + "/accounts");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyCustomerAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		customerAccounts = JSONUtils.cast(bodyCustomerAccount.getData(), CustomerAccountsDto.class);
		assertTrue(customerAccounts.getAccounts().size() == 1);

		OperationDto operation = new OperationDto();
		operation.setAmmount(0);
		operation.setType(OperationType.IN);

		response = sendAsyncPostRequest("/accounts/9999999999", JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Account not found"));

		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Invalid ammount."));

		operation.setAmmount(100);
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountFirstCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);
		assertTrue(accountFirstCostumer.getBalance() == operation.getAmmount());

		response = sendAsyncGetRequest("/accounts/" + accountFirstCostumer.getNumber());
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		AccountFullDto account = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);
		assertTrue(accountFirstCostumer.getBalance() == account.getBalance());

		operation.setAmmount(0);
		operation.setType(OperationType.OUT);

		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Invalid ammount."));

		operation.setAmmount(110);
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("There's no balance enough to do this transaction."));

		operation.setAmmount(10);
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountFirstCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);
		assertTrue(accountFirstCostumer.getBalance() == 90);

		operation.setAmmount(0);
		operation.setType(OperationType.TRANSFER);

		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Destination account is missing"));

		operation.setToAccountNumber("999999999999");
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Destination account not found."));

		operation.setToAccountNumber(accountFirstCostumer.getNumber());
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Origin and Destination accounts cannot be the same."));

		operation.setToAccountNumber(accountSecondCostumer.getNumber());
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("Invalid ammount."));

		operation.setAmmount(110);
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		assertTrue(bodyAccount.getErrors().contains("There's no balance enough to do this transaction."));

		operation.setAmmount(50);
		response = sendAsyncPostRequest("/accounts/" + accountFirstCostumer.getNumber(),
				JSONUtils.convertObjectToJsonString(operation));
		assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountFirstCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);
		assertTrue(accountFirstCostumer.getBalance() == 40);

		response = sendAsyncGetRequest("/accounts/" + accountSecondCostumer.getNumber());
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		accountSecondCostumer = JSONUtils.cast(bodyAccount.getData(), AccountFullDto.class);
		assertTrue(accountSecondCostumer.getBalance() == operation.getAmmount());

		response = sendAsyncGetRequest("/accounts/99999999/bankStatement");
		assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

		ResponseBody<BankStatementDto> bankStatement = JSONUtils.convertJsonToObject(response.getContentAsString(),
				ResponseBody.class);
		assertTrue(bankStatement.getErrors().contains("Account not found"));

		response = sendAsyncGetRequest("/accounts/" + accountFirstCostumer.getNumber() + "/bankStatement");
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

		bodyAccount = JSONUtils.convertJsonToObject(response.getContentAsString(), ResponseBody.class);
		BankStatementDto statement = JSONUtils.cast(bodyAccount.getData(), BankStatementDto.class);
		assertTrue(statement.getBankStatement().size() == 3);
	}

	public MockHttpResponse sendAsyncGetRequest(String path) throws URISyntaxException {
		MockHttpRequest request = MockHttpRequest.get(path);
		request.accept(MediaType.APPLICATION_JSON);

		MockHttpResponse response = new MockHttpResponse();
		SynchronousExecutionContext synchronousExecutionContext = new SynchronousExecutionContext(
				(SynchronousDispatcher) dispatcher, request, response);
		request.setAsynchronousContext(synchronousExecutionContext);
		return sendHttpRequest(request, response);
	}

	public MockHttpResponse sendAsyncPostRequest(String path, String requestBody) throws URISyntaxException {

		MockHttpRequest request = MockHttpRequest.post(path);
		request.accept(MediaType.APPLICATION_JSON);
		request.contentType(MediaType.APPLICATION_JSON_TYPE);
		request.content(requestBody.getBytes());

		MockHttpResponse response = new MockHttpResponse();
		SynchronousExecutionContext synchronousExecutionContext = new SynchronousExecutionContext(
				(SynchronousDispatcher) dispatcher, request, response);
		request.setAsynchronousContext(synchronousExecutionContext);
		return sendHttpRequest(request, response);
	}

	private MockHttpResponse sendHttpRequest(MockHttpRequest request, MockHttpResponse response) {
		dispatcher.invoke(request, response);
		return response;
	}
}
