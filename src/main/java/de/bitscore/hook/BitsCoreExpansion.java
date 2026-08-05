package de.bitscore.hook;

import de.bitscore.api.BitsCoreAPI;
import de.bitscore.api.BitsProvider;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.Locale;

public class BitsCoreExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "bitscore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "BlockException_";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "0";
        }

        BitsProvider provider;
        try {
            provider = BitsCoreAPI.getProvider();
        } catch (Exception e) {
            return "0";
        }

        int balance = provider.getBalance(player.getUniqueId());

        if (params.equalsIgnoreCase("balance")) {
            return String.valueOf(balance);
        }

        if (params.equalsIgnoreCase("balance_formatted")) {
            NumberFormat format = NumberFormat.getInstance(Locale.GERMAN);
            return format.format(balance);
        }

        if (params.equalsIgnoreCase("balance_short")) {
            return formatShort(balance);
        }

        return "0";
    }

    private String formatShort(int balance) {
        if (balance < 1_000) {
            return String.valueOf(balance);
        } else if (balance < 1_000_000) {
            return String.format(Locale.GERMAN, "%.1fk", balance / 1_000.0);
        } else if (balance < 1_000_000_000) {
            return String.format(Locale.GERMAN, "%.1fM", balance / 1_000_000.0);
        } else {
            return String.format(Locale.GERMAN, "%.1fB", balance / 1_000_000_000.0);
        }
    }
}
