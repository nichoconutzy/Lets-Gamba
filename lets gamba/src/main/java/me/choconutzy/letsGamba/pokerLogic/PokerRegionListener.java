package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.pokerLogic.PokerManager;
import me.choconutzy.letsGamba.pokerLogic.PokerTable;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PokerRegionListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        // 1. Optimization: Only run logic if the player moved to a new BLOCK
        // (Prevents code running 20 times a second just for looking around)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // 2. Check all active tables
        for (PokerTable table : PokerManager.getActiveTables()) {

            // This method in your PokerTable.java already handles the 5x4 expansion
            boolean inside = table.isInsideTableArea(p);
            boolean alreadyJoined = table.hasPlayer(p.getUniqueId());

            // --- Case A: Player walked INTO the 5x4 box ---
            if (inside && !alreadyJoined) {
                // FORCE PLAYER TO RUN COMMAND
                // We append the ID so they join the specific table they walked into
                p.performCommand("poker join " + table.getTableId());

                // Note: We removed the manual p.sendMessage here because the
                // command itself usually sends a "You sat down" message.
            }

            // --- Case B: Player walked OUT OF the 5x4 box ---
            else if (!inside && alreadyJoined) {
                // FORCE PLAYER TO RUN LEAVE COMMAND
                p.performCommand("poker leave");

                // Note: Same as above, the command likely handles the message
                // so we don't send a duplicate message here.
            }
        }
    }
}