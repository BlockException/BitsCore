package de.bitscore.api;

import java.util.UUID;

public interface BitsProvider {

    int getBalance(UUID uuid);

    boolean addBits(UUID uuid, int amount, String reason);

    boolean removeBits(UUID uuid, int amount, String reason);

    void setBalance(UUID uuid, int amount);

    boolean hasEnough(UUID uuid, int amount);
}
