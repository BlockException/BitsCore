package de.bitscore.database;

import de.bitscore.BitsPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitsRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final String serverName;

    public BitsRepository(DatabaseManager databaseManager, Logger logger, String serverName) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.serverName = serverName;
    }

    public void createIfNotExists(UUID uuid, String playerName) {
        String sql = "INSERT IGNORE INTO player_bits (uuid, player_name) VALUES (?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create player record for " + uuid, e);
        }
    }

    public int loadBalance(UUID uuid) {
        String sql = "SELECT balance FROM player_bits WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("balance");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not load balance for " + uuid, e);
        }
        return 0;
    }

    public void saveBalance(UUID uuid, int balance) {
        String sql = "UPDATE player_bits SET balance = ? WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, balance);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not save balance for " + uuid, e);
        }
    }

    public void atomicAdd(UUID uuid, int amount) {
        String sql = "UPDATE player_bits SET balance = balance + ? WHERE uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not add bits for " + uuid, e);
        }
    }

    public boolean atomicRemove(UUID uuid, int amount) {
        String sql = "UPDATE player_bits SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setString(2, uuid.toString());
            ps.setInt(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not remove bits for " + uuid, e);
            return false;
        }
    }

    public void logTransaction(UUID uuid, int amount, String reason) {
        String sql = "INSERT INTO bits_transactions (uuid, amount, reason, server) VALUES (?, ?, ?, ?)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, amount);
            ps.setString(3, reason);
            ps.setString(4, serverName);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not log transaction for " + uuid, e);
        }
    }

    public List<BitsPlayer> getTopBalances(int limit) {
        List<BitsPlayer> topPlayers = new ArrayList<>();
        String sql = "SELECT uuid, player_name, balance FROM player_bits ORDER BY balance DESC LIMIT ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("player_name");
                    int balance = rs.getInt("balance");
                    topPlayers.add(new BitsPlayer(uuid, name, balance));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not get top balances", e);
        }
        return topPlayers;
    }
}
