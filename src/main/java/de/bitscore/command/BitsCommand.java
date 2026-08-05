package de.bitscore.command;

import de.bitscore.BitsPlayer;
import de.bitscore.database.BitsRepository;
import de.bitscore.provider.BitsCoreProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BitsCommand implements CommandExecutor, TabCompleter {

    private final BitsCoreProvider provider;
    private final BitsRepository repository;

    public BitsCommand(BitsCoreProvider provider, BitsRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "balance":
            case "bal":
                handleBalance(sender, args);
                break;
            case "give":
            case "add":
                handleGive(sender, args);
                break;
            case "take":
            case "remove":
                handleTake(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "top":
                handleTop(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "You must specify a player name.");
                return;
            }
            Player player = (Player) sender;
            int balance = provider.getBalance(player.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "Your balance: " + ChatColor.GOLD + balance + " Bits");
        } else {
            String targetName = args[1];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                int balance = provider.getBalance(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + target.getName() + "'s balance: " + ChatColor.GOLD + balance + " Bits");
            } else {
                UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
                CompletableFuture.supplyAsync(() -> repository.loadBalance(uuid))
                        .thenAccept(balance -> {
                            sender.sendMessage(ChatColor.GREEN + targetName + "'s balance: " + ChatColor.GOLD + balance + " Bits");
                        });
            }
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bits.core.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bits give <player> <amount>");
            return;
        }

        String targetName = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return;
        }

        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        provider.addBits(uuid, amount, "admin_give");
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Bits to " + targetName + ".");
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bits.core.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bits take <player> <amount>");
            return;
        }

        String targetName = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
            return;
        }

        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        boolean success = provider.removeBits(uuid, amount, "admin_take");
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Took " + amount + " Bits from " + targetName + ".");
        } else {
            sender.sendMessage(ChatColor.RED + targetName + " does not have enough Bits.");
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bits.core.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /bits set <player> <amount>");
            return;
        }

        String targetName = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount.");
            return;
        }

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
            return;
        }

        UUID uuid = Bukkit.getOfflinePlayer(targetName).getUniqueId();
        provider.setBalance(uuid, amount);
        sender.sendMessage(ChatColor.GREEN + "Set " + targetName + "'s balance to " + amount + " Bits.");
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("bits.core.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do this.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "--- Top 10 Bits Balances ---");
        CompletableFuture.supplyAsync(() -> repository.getTopBalances(10))
                .thenAccept(topPlayers -> {
                    int rank = 1;
                    for (BitsPlayer bp : topPlayers) {
                        sender.sendMessage(ChatColor.YELLOW + String.valueOf(rank) + ". " + bp.getName() + " - " + ChatColor.GOLD + bp.getBalance() + " Bits");
                        rank++;
                    }
                });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- BitsCore Help ---");
        sender.sendMessage(ChatColor.YELLOW + "/bits balance [player]" + ChatColor.WHITE + " - View balance");
        if (sender.hasPermission("bits.core.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/bits top" + ChatColor.WHITE + " - View top balances");
            sender.sendMessage(ChatColor.YELLOW + "/bits add,give,set,remove,take <player> <amount>" + ChatColor.WHITE + " - Manage bits");
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("balance");
            if (sender.hasPermission("bits.core.admin")) {
                completions.add("top");
                completions.add("give");
                completions.add("take");
                completions.add("set");
                completions.add("add");
                completions.add("remove");
            }
            return completions.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("balance") || sub.equals("give") || sub.equals("take") || sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
                return null;
            }
        }
        return completions;
    }
}
