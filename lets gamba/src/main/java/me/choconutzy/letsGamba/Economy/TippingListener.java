package me.choconutzy.letsGamba.Economy;

import me.choconutzy.letsGamba.LetsGambaPlugin;
import me.choconutzy.letsGamba.pokerLogic.PokerManager;
import me.choconutzy.letsGamba.pokerLogic.PokerTable;
import me.choconutzy.letsGamba.pokerLogic.nitwitDealer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TippingListener implements Listener {

    private final Map<UUID, nitwitDealer> pendingCustomTips = new HashMap<>();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.BLACK + "Tip the Dealer?")) return;

        event.setCancelled(true); // Stop player taking items

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        // Find the correct dealer using PokerManager
        nitwitDealer dealer = getDealerForPlayer(player);

        if (dealer == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Could not find the dealer you are tipping.");
            return;
        }

        Material type = event.getCurrentItem().getType();
        player.closeInventory();

        if (type == Material.IRON_NUGGET) {
            dealer.processTip(player, 10.0);
        } else if (type == Material.GOLD_NUGGET) {
            dealer.processTip(player, 20.0);
        } else if (type == Material.EMERALD) {
            dealer.processTip(player, 30.0);
        } else if (type == Material.NAME_TAG) {
            player.sendMessage(ChatColor.GREEN + "Type the amount you want to tip in chat.");
            pendingCustomTips.put(player.getUniqueId(), dealer);

            // Remove player from map after 15 seconds so they don't accidentally tip later
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (pendingCustomTips.containsKey(player.getUniqueId())) {
                        pendingCustomTips.remove(player.getUniqueId());
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.YELLOW + "Tipping request timed out.");
                        }
                    }
                }
            }.runTaskLater(LetsGambaPlugin.getInstance(), 300L);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingCustomTips.containsKey(player.getUniqueId())) return;

        event.setCancelled(true); // Hide the number from public chat

        nitwitDealer dealer = pendingCustomTips.remove(player.getUniqueId());
        String message = event.getMessage();

        try {
            double amount = Double.parseDouble(message);

            // Run back on main thread for Bukkit API safety
            new BukkitRunnable() {
                @Override
                public void run() {
                    dealer.processTip(player, amount);
                }
            }.runTask(LetsGambaPlugin.getInstance());

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "That is not a valid number.");
        }
    }
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            // Check if this villager is a registered dealer
            PokerTable table = PokerManager.findNearestTable(event.getRightClicked().getLocation());
            if (table != null && table.nitwitDealer.getDealerId().equals(event.getRightClicked().getUniqueId())) {
                event.setCancelled(true); // Don't open trade menu
                table.nitwitDealer.openTippingMenu(event.getPlayer());
            }
        }
    }
    /**
     * Tries to find the Dealer associated with the player.
     * 1. Checks if player is seated at a table.
     * 2. If not seated, checks if player is standing near a table.
     */
    private nitwitDealer getDealerForPlayer(Player player) {
        // 1. Check if seated
        PokerTable table = PokerManager.getTableOfPlayer(player);

        // 2. If not seated, find nearest table (within 20 blocks)
        if (table == null) {
            table = PokerManager.findNearestTable(player.getLocation());
        }

        if (table != null) {
            return table.nitwitDealer; // Assuming accessing the field directly is allowed
        }
        return null;
    }
}