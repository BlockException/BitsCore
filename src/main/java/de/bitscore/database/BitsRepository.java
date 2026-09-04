package de.bitscore.database;

import de.bitscore.BitsPlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BitsRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final String serverName;
    private final boolean localMode;
    private final File localStorageFile;
    private final YamlConfiguration localStorage;
    private final Object localLock;

    private BitsRepository(
            DatabaseManager databaseManager,
            Logger logger,
            String serverName,
            boolean localMode,
            File localStorageFile,
            YamlConfiguration localStorage
    ) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.serverName = serverName;
        this.localMode = localMode;
        this.localStorageFile = localStorageFile;
        this.localStorage = localStorage;
        this.localLock = localMode ? new Object() : null;
    }

    public static BitsRepository forDatabase(DatabaseManager databaseManager, Logger logger, String serverName) {
        return new BitsRepository(databaseManager, logger, serverName, false, null, null);
    }

    public static BitsRepository forLocal(File dataFolder, Logger logger, String serverName) {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder for local Bits storage.");
        }

        File storageFile = new File(dataFolder, "local-bits.yml");
        YamlConfiguration storage = YamlConfiguration.loadConfiguration(storageFile);
        return new BitsRepository(null, logger, serverName, true, storageFile, storage);
    }

    public void createIfNotExists(UUID uuid, String playerName) {
        if (localMode) {
            synchronized (localLock) {
                String basePath = "players." + uuid;
                boolean changed = false;
                if (!localStorage.contains(basePath + ".balance")) {
                    localStorage.set(basePath + ".balance", 0);
                    changed = true;
                }
                if (playerName != null && !playerName.isBlank()) {
                    localStorage.set(basePath + ".name", playerName);
                    changed = true;
                }
                if (changed) {
                    saveLocalStorageOrThrow();
                }
            }
            return;
        }

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
        if (localMode) {
            synchronized (localLock) {
                return localStorage.getInt("players." + uuid + ".balance", 0);
            }
        }

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
        if (localMode) {
            synchronized (localLock) {
                String basePath = "players." + uuid;
                localStorage.set(basePath + ".balance", balance);
                if (!localStorage.contains(basePath + ".name")) {
                    localStorage.set(basePath + ".name", uuid.toString());
                }
                saveLocalStorageOrThrow();
            }
            return;
        }

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
        if (localMode) {
            synchronized (localLock) {
                String path = "players." + uuid + ".balance";
                int balance = localStorage.getInt(path, 0);
                localStorage.set(path, balance + amount);
                if (!localStorage.contains("players." + uuid + ".name")) {
                    localStorage.set("players." + uuid + ".name", uuid.toString());
                }
                saveLocalStorageOrThrow();
            }
            return;
        }

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
        if (localMode) {
            synchronized (localLock) {
                String path = "players." + uuid + ".balance";
                int balance = localStorage.getInt(path, 0);
                if (balance < amount) {
                    return false;
                }
                localStorage.set(path, balance - amount);
                if (!localStorage.contains("players." + uuid + ".name")) {
                    localStorage.set("players." + uuid + ".name", uuid.toString());
                }
                saveLocalStorageOrThrow();
                return true;
            }
        }

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
        if (localMode) {
            synchronized (localLock) {
                String key = "transactions." + System.currentTimeMillis() + "-" + UUID.randomUUID();
                localStorage.set(key + ".uuid", uuid.toString());
                localStorage.set(key + ".amount", amount);
                localStorage.set(key + ".reason", reason);
                localStorage.set(key + ".server", serverName);
                saveLocalStorageOrThrow();
            }
            return;
        }

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
        if (localMode) {
            synchronized (localLock) {
                List<BitsPlayer> topPlayers = new ArrayList<>();
                ConfigurationSection playersSection = localStorage.getConfigurationSection("players");
                if (playersSection == null) {
                    return topPlayers;
                }

                for (String key : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        String name = localStorage.getString("players." + key + ".name", key);
                        int balance = localStorage.getInt("players." + key + ".balance", 0);
                        topPlayers.add(new BitsPlayer(uuid, name, balance));
                    } catch (IllegalArgumentException ignored) {
                        logger.log(Level.WARNING, "Skipping invalid UUID in local bits storage: " + key);
                    }
                }

                topPlayers.sort(Comparator.comparingInt(BitsPlayer::getBalance).reversed());
                if (topPlayers.size() > limit) {
                    return new ArrayList<>(topPlayers.subList(0, limit));
                }
                return topPlayers;
            }
        }

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

    private void saveLocalStorageOrThrow() {
        try {
            localStorage.save(localStorageFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist local Bits storage file.", e);
        }
    }
}
