package de.bitscore;

import de.bitscore.api.BitsCoreAPI;
import de.bitscore.command.BitsCommand;
import de.bitscore.database.BitsRepository;
import de.bitscore.database.DatabaseManager;
import de.bitscore.hook.BitsCoreExpansion;
import de.bitscore.listener.BitsListener;
import de.bitscore.provider.BitsCoreProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public class BitsCorePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BitsCoreProvider provider;
    private BukkitTask autoSaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boolean disableDatabase = getConfig().getBoolean("disable-database", true);
        BitsRepository repository;

        if (disableDatabase) {
            repository = BitsRepository.forLocal(getDataFolder(), getLogger(), getServer().getName());
            getLogger().warning("Database is disabled (disable-database: true). Using local file storage.");
        } else {
            try {
                this.databaseManager = new DatabaseManager(getConfig());
            } catch (RuntimeException e) {
                getLogger().log(Level.WARNING, "MySQL is enabled but unavailable: " + e.getMessage());
                getLogger().warning("Falling back to local file storage (plugins/BitsCore/local-bits.yml).");
                repository = BitsRepository.forLocal(getDataFolder(), getLogger(), getServer().getName());
                this.databaseManager = null;
                initializePluginComponents(repository);
                getLogger().info("BitsCore enabled successfully (local fallback mode).");
                return;
            }
            repository = BitsRepository.forDatabase(databaseManager, getLogger(), getServer().getName());
        }

        initializePluginComponents(repository);
        getLogger().info("BitsCore enabled successfully.");
    }

    private void initializePluginComponents(BitsRepository repository) {
        this.provider = new BitsCoreProvider(repository);

        BitsCoreAPI.setProvider(provider);
        getServer().getPluginManager().registerEvents(new BitsListener(repository, provider), this);

        long ticksIn5Minutes = 20L * 60L * 5L;
        this.autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> provider.saveAllCached(),
                ticksIn5Minutes,
                ticksIn5Minutes
        );

        BitsCommand command = new BitsCommand(provider, repository);
        getCommand("bits").setExecutor(command);
        getCommand("bits").setTabCompleter(command);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BitsCoreExpansion().register();
            getLogger().info("PlaceholderAPI Hook erfolgreich registriert!");
        }
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        if (provider != null) {
            provider.saveAllCached().join();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("BitsCore disabled.");
    }
}
