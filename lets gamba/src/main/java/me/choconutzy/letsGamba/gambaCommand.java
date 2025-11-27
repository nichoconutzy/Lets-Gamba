package me.choconutzy.letsGamba;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class gambaCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        // /poker  -> Host/Create a table (Only the host does this)
        if (args.length == 0) {
            PokerManager.hostTable(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /poker join -> Join an existing table
        if (sub.equals("join") || sub.equals("j")) {
            // Support for /poker join <ID>
            String idArg = (args.length > 1) ? args[1] : "";
            PokerManager.joinTable(player, idArg);
            return true;
        }

        // /poker leave
        if (sub.equals("leave")) {
            PokerManager.leaveTable(player);
            return true;
        }

        // /poker leave
        if (sub.equals("leave")) {
            PokerManager.leaveTable(player);
            player.sendMessage(ChatColor.RED + "You have left the poker table.");
            return true;
        }

        // /poker fold
        if (sub.equals("fold") || sub.equals("f")) {
            // NEW: Route action via Manager to find the player's specific table
            PokerManager.handleAction(player, PokerAction.FOLD);
            return true;
        }

        // /poker call | check
        if (sub.equals("call") || sub.equals("check") || sub.equals("c")) {
            doCall(player);
            return true;
        }
        // /poker raise | bet | r
        if (sub.equals("raise") || sub.equals("bet") || sub.equals("r")) {
            PokerManager.handleAction(player, PokerAction.RAISE);
            return true;
        }

        // Unknown usage
        player.sendMessage(ChatColor.RED + "Unknown poker command. Usage:");
        player.sendMessage(ChatColor.RED + "/poker " + ChatColor.GRAY + "(Host a new table)");
        player.sendMessage(ChatColor.RED + "/poker join " + ChatColor.GRAY + "(Join existing table)");
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