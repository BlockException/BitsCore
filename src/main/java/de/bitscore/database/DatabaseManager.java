package de.bitscore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(FileConfiguration config, Logger logger) {
        this.logger = logger;
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

        this.dataSource = new HikariDataSource(hikariConfig);

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
            logger.log(Level.SEVERE, "Could not create database tables", e);
        }
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
