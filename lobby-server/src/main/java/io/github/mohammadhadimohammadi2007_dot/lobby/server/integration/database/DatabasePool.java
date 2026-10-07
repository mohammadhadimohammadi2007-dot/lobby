package io.github.mohammadhadimohammadi2007_dot.lobby.server.integration.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.config.IntegrationsConfig;
import io.github.mohammadhadimohammadi2007_dot.lobby.server.util.Async;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The one MariaDB connection pool shared by every database integration.
 *
 * <p>Only created when at least one database integration is enabled. All queries run on virtual
 * threads through {@link #query}; never call {@link #queryNow} from the tick thread.
 */
public final class DatabasePool implements AutoCloseable {

    private static final long CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;
    private static final int MIN_IDLE = 1;
    /** Fail fast instead of retrying forever when the database is down at startup. */
    private static final long NO_RETRY_ON_START = -1;

    private final HikariDataSource dataSource;

    private DatabasePool(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Opens the pool and checks that the database answers.
     *
     * @throws SQLException with a readable message if the database cannot be reached
     */
    public static DatabasePool connect(IntegrationsConfig.Database settings) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("lobby-database");
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(Math.min(MIN_IDLE, settings.poolSize()));
        config.setConnectionTimeout(CONNECT_TIMEOUT_MILLIS);
        config.setInitializationFailTimeout(NO_RETRY_ON_START);

        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new SQLException("the database did not answer a test query");
            }
        } catch (SQLException e) {
            dataSource.close();
            throw new SQLException("Cannot connect to the database " + settings + ": " + rootCause(e).getMessage()
                    + ". Check host, port, database name, username and password in integrations.yml,"
                    + " and that the database server is running.", e);
        }
        return new DatabasePool(dataSource);
    }

    /** The original error behind Hikari's generic "connection is not available" message. */
    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Runs {@code work} with a connection on a virtual thread. */
    public <T> CompletableFuture<T> query(SqlWork<T> work) {
        return Async.supply(() -> {
            try {
                return queryNow(work);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Runs {@code work} with a connection on the current thread. Only for code that already runs off the
     * tick thread, such as async login events.
     */
    public <T> T queryNow(SqlWork<T> work) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }

    /** A piece of database work that receives a pooled connection. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
