package me.choconutzy.letsGamba.Commands;

import dev.geco.gsit.api.event.EntitySitEvent;
import me.choconutzy.letsGamba.LetsGambaPlugin;
import me.choconutzy.letsGamba.pokerLogic.PokerManager;
import me.choconutzy.letsGamba.pokerLogic.PokerTable;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

public class GSitListener implements Listener {

    @EventHandler
    public void onEntitySit(EntitySitEvent event) {
        Entity ent = event.getEntity();
        if (!(ent instanceof Player)) return;
        Player player = (Player) ent;

        if (event.getSeat() == null) return;

        // Prefer seat location -> block (safer across API versions)
        Block seatBlock = null;
        try {
            // Some GSit API versions expose getBlock() on Seat, others expose getLocation()
            Object seat = event.getSeat();
            // try seat.getBlock() via reflection first
            try {
                seatBlock = (Block) seat.getClass().getMethod("getBlock").invoke(seat);
            } catch (NoSuchMethodException ignored) {
                // fallback to getLocation().getBlock()
                Object loc = seat.getClass().getMethod("getLocation").invoke(seat);
                seatBlock = (Block) loc.getClass().getMethod("getBlock").invoke(loc);
            }
        } catch (Exception ex) {
            // reflection failed — fallback: use player's location block as last resort
            seatBlock = player.getLocation().getBlock();
        }

        if (seatBlock == null) return;

        PokerTable table = PokerManager.getTableByBlock(seatBlock);
        if (table == null) return;

        player.sendMessage(ChatColor.GREEN + "Sitting down at Poker Table #" + table.getTableId() + "...");

        // Delay just a few ticks so GSit finishes the sit animation
        LetsGambaPlugin.getInstance().getServer().getScheduler().runTaskLater(
                LetsGambaPlugin.getInstance(),
                () -> table.join(player),
                5L
        );
    }
}
