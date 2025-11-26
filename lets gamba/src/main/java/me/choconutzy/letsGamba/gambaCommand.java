package me.choconutzy.letsGamba;

import java.math.BigDecimal;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class gambaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        // /poker  -> join/start global table
        if (args.length == 0) {
            PokerManager.joinTable(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /poker leave
        if (sub.equals("leave")) {
            PokerManager.leaveTable(player);
            return true;
        }

        // /poker info
        if (sub.equals("info")) {
            PokerManager.showTableInfo(player);
            return true;
        }

        // /poker fold
        if (sub.equals("fold") || sub.equals("f")) {
            doFold(player);
            return true;
        }

        // /poker call | check
        if (sub.equals("call") || sub.equals("check") || sub.equals("c")) {
            doCall(player);
            return true;
        }
        // /poker raise | bet | r  (default raise, no custom amount for now)
        if (sub.equals("raise") || sub.equals("bet") || sub.equals("r")) {
            PokerManager.handleAction(player, PokerAction.RAISE);
            return true;
        }

        

        // ----- /poker <playerName>  ----------------------------------------
        // If it wasn't leave/fold/call/raise, treat first arg as a player name
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);

            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player '" + args[0] + "' is not online.");
                return true;
            }

            // For now we still use the single global table, just with nicer messaging
            PokerManager.joinTable(player);
            player.sendMessage(ChatColor.GREEN + "You join the poker table with "
                    + ChatColor.GOLD + target.getName() + ChatColor.GREEN + ".");
            return true;
        }

        // Unknown usage (extra args, etc.)
        player.sendMessage(ChatColor.RED + "Unknown poker command. Usage:");
        player.sendMessage(ChatColor.RED + "/poker");
        player.sendMessage(ChatColor.RED + "/poker <player>");
        player.sendMessage(ChatColor.RED + "/poker leave");
        player.sendMessage(ChatColor.RED + "/poker fold | call | raise");
        return true;
    }

    // ---- Action helpers ------------------------------------------------

    private void doFold(Player player) {
        PokerManager.handleAction(player, PokerAction.FOLD);
    }

    private void doCall(Player player) {
        PokerManager.handleAction(player, PokerAction.CHECK_OR_CALL);
    }

    private void doRaise(Player player) {
        PokerManager.handleAction(player, PokerAction.RAISE);
    }

    // ---- Chat buttons menu ---------------------------------------------

    public void sendActionMenu(Player player) {
        TextComponent base = new TextComponent(ChatColor.GOLD + "Choose action:");

        // [FOLD]
        TextComponent fold = new TextComponent(" " + ChatColor.RED + "[FOLD]");
        fold.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poker fold"));
        fold.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.RED + "Fold your hand").create()));

        // [CHECK / CALL]
        TextComponent call = new TextComponent(" " + ChatColor.YELLOW + "[CHECK / CALL]");
        call.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poker call"));
        call.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.YELLOW + "Check or call").create()));

        // [RAISE]
        TextComponent raise = new TextComponent(" " + ChatColor.GREEN + "[RAISE]");
        raise.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poker raise"));
        raise.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.GREEN + "Raise the bet").create()));

        base.addExtra(fold);
        base.addExtra(call);
        base.addExtra(raise);

        player.spigot().sendMessage(base);
    }
}
