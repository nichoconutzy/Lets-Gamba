package me.choconutzy.letsGamba;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PokerManager {

    private static final Map<Integer, PokerTable> tables = new HashMap<>();
    private static int nextTableId = 1;

    // --- HOSTING ---

    public static void hostTable(Player player) {
        // Check if player is already playing
        if (getTableOfPlayer(player) != null) {
            player.sendMessage(ChatColor.RED + "You are already seated at a table!");
            return;
        }

        // Create new instance
        PokerTable newTable = new PokerTable(nextTableId);

        // Attempt setup (finding wool/villager)
        boolean success = newTable.setupDealerAndTable(player);

        if (success) {
            // Check for physical overlap with existing tables
            for (PokerTable t : tables.values()) {
                if (t.isLocationOverlapping(newTable.getCenter())) {
                    player.sendMessage(ChatColor.RED + "This table is too close to an existing poker table.");
                    newTable.forceCleanup();
                    return;
                }
            }

            tables.put(nextTableId, newTable);
            player.sendMessage(ChatColor.GREEN + "Table #" + nextTableId + " initialized!");
            nextTableId++;
        }
    }

    // --- JOINING ---
    public static void joinTable(Player player, String arg) {
        PokerTable existing = getTableOfPlayer(player);
        if (existing != null) {
            player.sendMessage(ChatColor.RED + "You are already at Table #" + existing.getTableId());
            return;
        }

        // /poker join (find nearest)
        if (arg == null || arg.isEmpty()) {
            PokerTable nearest = findNearestTable(player.getLocation());
            if (nearest != null) {
                nearest.join(player);
            } else {
                player.sendMessage(ChatColor.RED + "No poker tables found nearby.");
            }
            return;
        }

        // /poker join <id>
        try {
            int id = Integer.parseInt(arg);
            PokerTable target = tables.get(id);
            if (target != null) {
                target.join(player);
            } else {
                player.sendMessage(ChatColor.RED + "Table #" + id + " does not exist.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid table ID.");
        }
    }

    // --- LEAVING ---
    public static void leaveTable(Player player) {
        PokerTable table = getTableOfPlayer(player);
        if (table != null) {
            table.leave(player);
            // If table is empty after leave, it is removed in the PokerTable.leave() logic
            // calling back to removeTable(), or handled internally there.
        } else {
            player.sendMessage(ChatColor.RED + "You are not seated at any table.");
        }
    }

    // --- ACTIONS ---
    public static void handleAction(Player player, PokerAction action) {
        PokerTable table = getTableOfPlayer(player);
        if (table != null) {
            table.handleAction(player, action);
        } else {
            player.sendMessage(ChatColor.RED + "You are not in a game.");
        }
    }

    // --- SHOW HAND ---
    public static void requestShowHand(Player player) {
        PokerTable table = getTableOfPlayer(player);

        if (table == null) {
            player.sendMessage(ChatColor.RED + "You are not seated at any poker table.");
            return;
        }

        table.requestShowHand(player);
    }


    // --- ALL IN ---
    public static void allIn(Player player) {
        PokerTable table = getTableOfPlayer(player);
        if (table == null) {
            player.sendMessage(ChatColor.RED + "You are not seated at any poker table.");
            return;
        }
        table.handleAllIn(player);
    }

    // --- UTILS ---

    public static PokerTable getTableOfPlayer(Player player) {
        for (PokerTable table : tables.values()) {
            if (table.hasPlayer(player.getUniqueId())) {
                return table;
            }
        }
        return null;
    }

    // Find table by block (For GSit)
    public static PokerTable getTableByBlock(Block block) {
        for (PokerTable table : tables.values()) {
            if (table.isBlockPartofTable(block)) {
                return table;
            }
        }
        return null;
    }

    private static PokerTable findNearestTable(Location loc) {
        PokerTable nearest = null;
        double minDist = Double.MAX_VALUE;

        for (PokerTable table : tables.values()) {
            if (table.getCenter() == null || !table.getCenter().getWorld().equals(loc.getWorld())) continue;

            double dist = table.getCenter().distance(loc);
            if (dist < 20.0 && dist < minDist) { // 20 blocks search radius
                minDist = dist;
                nearest = table;
            }
        }
        return nearest;
    }

    public static void removeTable(int tableId) {
        tables.remove(tableId);
    }

    public static void showTableInfo(Player player) {
        PokerTable table = getTableOfPlayer(player);
        if (table != null) {
            table.sendInfo(player);
        } else {
            player.sendMessage(ChatColor.YELLOW + "Active Tables: " + tables.size());
            for (PokerTable t : tables.values()) {
                player.sendMessage(ChatColor.GRAY + "ID: " + t.getTableId() + " - Players: " + t.getSeatCount() + "/6");
            }
        }
    }

    public static void shutdownAll() {
        for (PokerTable t : tables.values()) {
            t.forceCleanup();
        }
        tables.clear();
    }
}