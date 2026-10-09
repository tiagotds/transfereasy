package br.com.tiagotds.transfereasy.infrastructure.persistence;

import br.com.tiagotds.transfereasy.infrastructure.config.DatabaseSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.h2.jdbcx.JdbcConnectionPool;

/** Owns the embedded in-memory H2 instance and its connection pool. */
public final class Database implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    private final JdbcConnectionPool pool;

    private Database(JdbcConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * @param name in-memory database name; use a unique one per instance to get full isolation
     */
    public static Database startInMemory(String name, DatabaseSettings settings) {
        var url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + settings.lockTimeout().toMillis();
        var pool = JdbcConnectionPool.create(url, "sa", "");
        pool.setMaxConnections(settings.poolSize());
        var database = new Database(pool);
        try {
            database.applySchema();
        } catch (RuntimeException e) {
            pool.dispose();
            throw e;
        }
        return database;
    }

    public Connection connection() throws SQLException {
        return pool.getConnection();
    }

    private void applySchema() {
        try (var in = Database.class.getResourceAsStream(SCHEMA_RESOURCE);
             var connection = pool.getConnection();
             Statement statement = connection.createStatement()) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + SCHEMA_RESOURCE);
            }
            statement.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | SQLException e) {
            throw new DatabaseException("Could not apply database schema", e);
        }
    }

    @Override
    public void close() {
        pool.dispose();
    }
}
