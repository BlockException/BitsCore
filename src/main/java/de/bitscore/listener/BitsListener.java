package de.bitscore.listener;

import de.bitscore.database.BitsRepository;
import de.bitscore.provider.BitsCoreProvider;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BitsListener implements Listener {

    private final BitsRepository repository;
    private final BitsCoreProvider provider;

    public BitsListener(BitsRepository repository, BitsCoreProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        CompletableFuture.runAsync(() -> {
            repository.createIfNotExists(uuid, name);
        }).thenCompose(v -> provider.loadIntoCache(uuid));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        provider.saveAndRemoveFromCache(uuid);
    }
}
