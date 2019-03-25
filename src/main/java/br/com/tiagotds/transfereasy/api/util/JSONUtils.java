package br.com.tiagotds.transfereasy.api.util;

import java.io.IOException;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public abstract class JSONUtils {

	private static final ObjectMapper mapper = new ObjectMapper();

	public static <T> T convertJsonToObject(String jsonSting, Class<T> clazz)
			throws JsonParseException, JsonMappingException, IOException {
		return mapper.readValue(jsonSting.getBytes(), clazz);
	}

	public static String convertObjectToJsonString(Object o)
			throws JsonParseException, JsonMappingException, IOException {
		return mapper.writeValueAsString(o);
	}

	public static <T> T cast(Object toCast, Class<T> toType) {
		return mapper.convertValue(toCast, toType);
	}

}
