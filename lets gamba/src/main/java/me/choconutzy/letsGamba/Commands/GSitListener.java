package me.choconutzy.letsGamba.Commands;

import dev.geco.gsit.api.event.EntitySitEvent;
import me.choconutzy.letsGamba.LetsGambaPlugin;
import me.choconutzy.letsGamba.pokerLogic.*;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class GSitListener implements Listener {

    @EventHandler
    public void onGSit(EntitySitEvent event) {
        // 1. Check if the "Entity" sitting is actually a Player
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();

        // 2. Get the Block they are trying to sit on
        if (event.getSeat() == null) return;

        Block seatBlock = event.getSeat().getBlock();

        // 3. Check if this block is part of a poker table
        PokerTable table = PokerManager.getTableByBlock(seatBlock);

        if (table != null) {
            // Check if player is already in that table
            if (table.hasPlayer(player.getUniqueId())) {
                return;
            }

            player.sendMessage(ChatColor.GREEN + "Sitting down at Poker Table #" + table.getTableId() + "...");

            // 4. Run join task (Delayed to let the sit animation finish)
            LetsGambaPlugin.getInstance().getServer().getScheduler().runTask(
                    LetsGambaPlugin.getInstance(),
                    () -> table.join(player)
            );
        }
    }
}