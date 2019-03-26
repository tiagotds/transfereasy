package br.com.tiagotds.transfereasy.api.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import br.com.tiagotds.transfereasy.api.dto.DefaultDto;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@Provider
public class TransfereasyExceptionHandler implements ExceptionMapper<TransfereasyException> {

	@Override
	public Response toResponse(TransfereasyException exception) {
		Status status;
		switch (exception.getType()) {
		case NOT_FOUND:
			status = Status.NOT_FOUND;
			break;
		case INVALID:
		case NO_BALANCE_ENOUGH:
		case ALREADY_EXISTS:
			status = Status.BAD_REQUEST;
			break;
		default:
			status = Status.INTERNAL_SERVER_ERROR;
		}

		ResponseBody<DefaultDto> response = new ResponseBody<>();
		response.getErrors().add(exception.getMessage());
		return Response.status(status).entity(response).build();
	}

}
