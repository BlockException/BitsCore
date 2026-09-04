package de.bitscore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final HikariDataSource dataSource;

    public DatabaseManager(FileConfiguration config) {
        HikariConfig hikariConfig = new HikariConfig();

        String host = config.getString("database.host", "127.0.0.1");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.name", "minecraft");
        String user = config.getString("database.user", "root");
        String password = config.getString("database.password", "password");

        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false");
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);

        hikariConfig.setMinimumIdle(config.getInt("pool.minimumIdle", 2));
        hikariConfig.setMaximumPoolSize(config.getInt("pool.maximumPoolSize", 10));

        try {
            this.dataSource = new HikariDataSource(hikariConfig);
        } catch (RuntimeException e) {
            throw buildInitializationException(e, host, port, database, user);
        }

        createTables();
    }

    private void createTables() {
        String createPlayerBits = "CREATE TABLE IF NOT EXISTS player_bits (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "balance INT NOT NULL DEFAULT 0, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String createTransactions = "CREATE TABLE IF NOT EXISTS bits_transactions (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "amount INT NOT NULL, " +
                "reason VARCHAR(64), " +
                "server VARCHAR(32), " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps1 = connection.prepareStatement(createPlayerBits);
             PreparedStatement ps2 = connection.prepareStatement(createTransactions)) {
            ps1.execute();
            ps2.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Database connected but table initialization failed. Check schema permissions for user.", e);
        }
    }

    private IllegalStateException buildInitializationException(RuntimeException cause, String host, int port, String database, String user) {
        Throwable rootCause = getRootCause(cause);
        String target = user + "@" + host + ":" + port + "/" + database;

        if (rootCause instanceof ConnectException || rootCause instanceof SocketTimeoutException || rootCause instanceof UnknownHostException) {
            return new IllegalStateException(
                    "MySQL host unreachable for '" + target + "'. Check host/port, firewall, and whether MySQL is running and reachable from this server.",
                    cause
            );
        }

        if (rootCause instanceof SQLException sqlException && "08S01".equals(sqlException.getSQLState())) {
            return new IllegalStateException(
                    "MySQL host unreachable for '" + target + "'. Check host/port, firewall, and whether MySQL is running and reachable from this server.",
                    cause
            );
        }

        if (rootCause instanceof SQLException sqlException && "28000".equals(sqlException.getSQLState())) {
            return new IllegalStateException(
                    "MySQL access denied for '" + target + "'. Verify username/password and allow this server host in MySQL grants (e.g. user@'%' or user@'<server-ip>').",
                    cause
            );
        }

        return new IllegalStateException("Could not initialize MySQL connection pool for '" + target + "'.", cause);
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
