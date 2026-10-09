package br.com.tiagotds.transfereasy.application.pipeline;

import br.com.tiagotds.transfereasy.application.command.Command;
import br.com.tiagotds.transfereasy.domain.model.Account;
import br.com.tiagotds.transfereasy.domain.model.Customer;
import br.com.tiagotds.transfereasy.domain.model.TransferReceipt;
import br.com.tiagotds.transfereasy.domain.port.IdempotencyStore.StoredResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialises command results for the idempotency store and fingerprints commands. Only an explicit allow-list of
 * result types can be stored or restored, so no class name read from the database is ever instantiated blindly.
 */
public final class ResultCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final Map<String, Class<?>> types = new LinkedHashMap<>();

    /** The results the API's commands produce. */
    public ResultCodec() {
        this(Account.class, Customer.class, TransferReceipt.class);
    }

    public ResultCodec(Class<?>... allowed) {
        for (var type : allowed) {
            types.put(type.getSimpleName(), type);
        }
    }

    /** SHA-256 of the command type and its canonical JSON: equal commands, equal fingerprints. */
    public String fingerprint(Command<?> command) {
        try {
            var canonical = command.getClass().getName() + ":" + MAPPER.writeValueAsString(command);
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not fingerprint " + command.getClass().getSimpleName(), e);
        }
    }

    public StoredResult encode(String fingerprint, Object value) {
        var type = value.getClass().getSimpleName();
        if (types.get(type) != value.getClass()) {
            throw new IllegalArgumentException("Result type not allowed in the idempotency store: " + type);
        }
        try {
            return new StoredResult(fingerprint, type, MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + type, e);
        }
    }

    public Object decode(StoredResult stored) {
        var type = types.get(stored.type());
        if (type == null) {
            throw new IllegalArgumentException("Unknown stored result type: " + stored.type());
        }
        try {
            return MAPPER.readValue(stored.json(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not restore " + stored.type(), e);
        }
    }
}
