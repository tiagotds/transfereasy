package br.com.tiagotds.transfereasy.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

/** Strict JSON codec: unknown fields, trailing tokens and null-for-primitive are all rejected. */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private Json() {
    }

    public static byte[] write(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise response", e);
        }
    }

    public static <T> T read(byte[] body, Class<T> type) throws InvalidJsonException {
        if (body == null || body.length == 0) {
            throw new InvalidJsonException("Request body is required.");
        }
        try {
            return MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new InvalidJsonException("Malformed or invalid JSON body: " + summarise(e));
        }
    }

    private static String summarise(IOException e) {
        var message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    public static final class InvalidJsonException extends Exception {
        public InvalidJsonException(String message) {
            super(message);
        }
    }
}
