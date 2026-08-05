package de.bitscore;

import java.util.UUID;

public class BitsPlayer {

    private final UUID uuid;
    private final String name;
    private final int balance;

    public BitsPlayer(UUID uuid, String name, int balance) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getBalance() {
        return balance;
    }
}
