package de.bitscore.provider;

import de.bitscore.api.BitsProvider;
import de.bitscore.database.BitsRepository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BitsCoreProvider implements BitsProvider {

    private final BitsRepository repository;
    private final Map<UUID, Integer> cache;

    public BitsCoreProvider(BitsRepository repository) {
        this.repository = repository;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public int getBalance(UUID uuid) {
        return cache.getOrDefault(uuid, repository.loadBalance(uuid));
    }

    @Override
    public boolean addBits(UUID uuid, int amount, String reason) {
        CompletableFuture.runAsync(() -> {
            repository.atomicAdd(uuid, amount);
            repository.logTransaction(uuid, amount, reason);
        });

        cache.computeIfPresent(uuid, (k, v) -> v + amount);
        return true;
    }

    @Override
    public boolean removeBits(UUID uuid, int amount, String reason) {
        boolean success = repository.atomicRemove(uuid, amount);
        if (success) {
            CompletableFuture.runAsync(() -> repository.logTransaction(uuid, -amount, reason));
            cache.computeIfPresent(uuid, (k, v) -> v - amount);
            return true;
        }
        return false;
    }

    @Override
    public void setBalance(UUID uuid, int amount) {
        CompletableFuture.runAsync(() -> repository.saveBalance(uuid, amount));
        cache.computeIfPresent(uuid, (k, v) -> amount);
    }

    @Override
    public boolean hasEnough(UUID uuid, int amount) {
        int balance = getBalance(uuid);
        return balance >= amount;
    }

    public CompletableFuture<Void> loadIntoCache(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> repository.loadBalance(uuid))
                .thenAccept(balance -> cache.put(uuid, balance));
    }

    public CompletableFuture<Void> saveAndRemoveFromCache(UUID uuid) {
        Integer balance = cache.remove(uuid);
        if (balance != null) {
            return CompletableFuture.runAsync(() -> repository.saveBalance(uuid, balance));
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> saveAllCached() {
        return CompletableFuture.runAsync(() -> {
            for (Map.Entry<UUID, Integer> entry : cache.entrySet()) {
                repository.saveBalance(entry.getKey(), entry.getValue());
            }
        });
    }
}
