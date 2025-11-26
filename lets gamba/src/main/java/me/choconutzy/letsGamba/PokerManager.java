package me.choconutzy.letsGamba;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.math.BigDecimal;

public class PokerManager {

    // Single global table for now
    private static final PokerTable TABLE = new PokerTable();

    public static void joinTable(Player player) {
        boolean wasEmpty = TABLE.isEmpty();

        TABLE.join(player);

        if (wasEmpty) {
            TABLE.startAfkChecker();
        }
        // If table was empty before this player joined, announce globally
        if (wasEmpty) {
            Bukkit.broadcastMessage(
                    ChatColor.GOLD + player.getName() +
                            ChatColor.AQUA + " has opened a poker table! " +
                            ChatColor.GRAY + "Use /poker to join."
            );
        }
    }

    public static void leaveTable(Player player) {
        TABLE.leave(player);
    }

    public static void showTableInfo(Player player) {
        TABLE.sendInfo(player);
    }

    public static void handleAction(Player player, PokerAction action) {
        TABLE.handleAction(player, action);
    }

    // Start AFK checker when plugin enables
    public static void startAfkChecker() {
        TABLE.startAfkChecker();
    }
}
