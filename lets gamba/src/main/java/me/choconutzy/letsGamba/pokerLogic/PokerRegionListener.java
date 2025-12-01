package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.pokerLogic.PokerManager;
import me.choconutzy.letsGamba.pokerLogic.PokerTable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PokerRegionListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Optimization: Only run on block changes
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player p = event.getPlayer();

        for (PokerTable table : PokerManager.getActiveTables()) {
            // This now uses the fixed method in PokerTable.java
            boolean inside = table.isInsideTableArea(p);
            boolean alreadyJoined = table.hasPlayer(p.getUniqueId());

            // Player walked IN
            if (inside && !alreadyJoined) {
                // If your command is "/poker join <id>", keep this:
                //p.performCommand("poker join " + table.getTableId());

                // If your command is ONLY "/poker join" (auto-finds table), use this instead:
                p.performCommand("poker join");
            }

            // Player walked OUT
            else if (!inside && alreadyJoined) {
                p.performCommand("poker leave");
            }
        }
    }
}