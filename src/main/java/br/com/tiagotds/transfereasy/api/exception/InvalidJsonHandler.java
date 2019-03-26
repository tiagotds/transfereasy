package br.com.tiagotds.transfereasy.api.exception;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.JsonParseException;

import br.com.tiagotds.transfereasy.api.dto.DefaultDto;
import br.com.tiagotds.transfereasy.api.util.ResponseBody;

@Provider
public class InvalidJsonHandler implements ExceptionMapper<JsonParseException> {

	@Override
	public Response toResponse(JsonParseException exception) {
		ResponseBody<DefaultDto> response = new ResponseBody<>();
		response.getErrors().add("Invalid request JSON body: Malformed JSON.");
		return Response.status(Status.BAD_REQUEST).entity(response).build();
	}

}
