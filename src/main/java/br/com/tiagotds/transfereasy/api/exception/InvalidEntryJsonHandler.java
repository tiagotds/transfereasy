package br.com.tiagotds.transfereasy.api.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;

import br.com.tiagotds.transfereasy.api.dto.DefaultDto;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@Provider
public class InvalidEntryJsonHandler implements ExceptionMapper<UnrecognizedPropertyException> {

	@Override
	public Response toResponse(UnrecognizedPropertyException exception) {
		ResponseBody<DefaultDto> response = new ResponseBody<>();
		response.getErrors()
				.add("Invalid request JSON body: " + exception.getUnrecognizedPropertyName() + " field is invalid.");
		return Response.status(Status.BAD_REQUEST).entity(response).build();
	}

}
