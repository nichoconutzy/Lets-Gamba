package me.choconutzy.letsGamba.Commands;

import me.choconutzy.letsGamba.pokerLogic.PokerAction;
import me.choconutzy.letsGamba.pokerLogic.PokerManager;
import me.choconutzy.letsGamba.pokerLogic.PokerTable;
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

        // /poker override - use this for debugging UI. Note that this is global and needs to be turned off once finished.
        if (sub.equals("override") || sub.equals("ov") || sub.equals("singleplayer")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "Only operators can use singleplayer override mode.");
                return true;
            }
            boolean currentlyEnabled = me.choconutzy.letsGamba.pokerLogic.PokerManager.isSingleplayerOverride();
            me.choconutzy.letsGamba.pokerLogic.PokerManager.setSingleplayerOverride(!currentlyEnabled, player);
            return true;
        }

        // /poker start
        if (sub.equals("start")) {
            // 1. Get the table the player is currently in
            PokerTable table = PokerManager.getTableOfPlayer(player);

            if (table == null) {
                player.sendMessage(ChatColor.RED + "You are not currently sitting at a poker table.");
                return true;
            }
            // 2. Attempt to start the game (Logic handled in PokerTable to keep Command clean)
            table.attemptStart(player);
            return true;
        }

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
            player.sendMessage(ChatColor.RED + "You have left the poker table.");
            return true;
        }

        // /poker info
        if (sub.equals("info")) {
            PokerManager.showTableInfo(player);
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
        // /poker raise <amount> | bet | r
        if (sub.equals("bet") || sub.equals("r")) {
            PokerManager.handleAction(player, PokerAction.RAISE);
            return true;
        }
        if (sub.equals("raise")) {
            if (args.length >= 2) {
                try {
                    java.math.BigDecimal raiseAmount = new java.math.BigDecimal(args[1]);
                    me.choconutzy.letsGamba.pokerLogic.PokerTable table = me.choconutzy.letsGamba.pokerLogic.PokerManager.getTableOfPlayer(player);
                    if (table != null) {
                        table.handleCustomRaise(player, raiseAmount);
                    } else {
                        player.sendMessage(org.bukkit.ChatColor.RED + "You are not at a poker table.");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "Invalid amount. Usage: /poker raise <amount>");
                }
            } else {
                // Fallback for chat button clicks without amount
                PokerManager.handleAction(player, PokerAction.RAISE);
            }
            return true;
        }

        // /poker showhand
        if (sub.equalsIgnoreCase("showhand") || sub.equalsIgnoreCase("show")) {
            PokerManager.requestShowHand(player);
            return true;
        }


        // /poker allin
        if (sub.equals("allin") || sub.equals("all-in") || sub.equals("ai")) {
            PokerManager.allIn(player);
            return true;
        }

        // Unknown usage
        player.sendMessage(ChatColor.RED + "Unknown poker command. Usage:");
        player.sendMessage(ChatColor.RED + "/poker " + ChatColor.GRAY + "(Host a new table)");
        player.sendMessage(ChatColor.RED + "/poker join " + ChatColor.GRAY + "(Join existing table)");
        player.sendMessage(ChatColor.RED + "/poker leave");
        player.sendMessage(ChatColor.RED + "/poker fold | call | raise");
        player.sendMessage(ChatColor.RED + "/poker override " + ChatColor.GRAY + "Toggle singleplayer override mode.");
        return true;
    }

    // ---- Action helpers ------------------------------------------------

    private void doFold(Player player) {
        PokerManager.handleAction(player, me.choconutzy.letsGamba.pokerLogic.PokerAction.FOLD);
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