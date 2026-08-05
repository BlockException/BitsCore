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

public class BitsCorePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private BitsCoreProvider provider;
    private BukkitTask autoSaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(getConfig(), getLogger());
        BitsRepository repository = new BitsRepository(databaseManager, getLogger(), getServer().getName());

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

        getLogger().info("BitsCore enabled successfully.");
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
