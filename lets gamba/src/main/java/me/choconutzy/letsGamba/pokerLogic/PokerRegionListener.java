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

        // Only trigger on block-transition (removes lag spam)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        // Check all active tables
        for (PokerTable table : PokerManager.getActiveTables()) {
            boolean inside = table.isInsideTableArea(p);
            boolean alreadyJoined = table.hasPlayer(p.getUniqueId());

            // Joined into the box (and not already playing)
            if (inside && !alreadyJoined) {
                // The join method handles seating and broadcast
                table.join(p);
                p.sendMessage(ChatColor.GOLD + "You sat down at Poker Table #" + table.getTableId());

                // Note: table.join() calls tryAutoStart() internally now,
                // checking for the 3-player threshold.
            }

            // Left the box (and was playing)
            else if (!inside && alreadyJoined) {
                table.leave(p);
                p.sendMessage(ChatColor.RED + "You stood up and left the table.");
            }
        }
    }
}