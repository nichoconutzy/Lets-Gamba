package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.Commands.gambaCommand;
import me.choconutzy.letsGamba.Economy.EconomyProvider;
import me.choconutzy.letsGamba.LetsGambaPlugin;
import me.choconutzy.letsGamba.handLogic.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Villager; // Added for Nitwit Integration
import org.bukkit.entity.Villager.Profession;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class PokerTable {

    // Seating order
    private final int tableId; // Table ID
    private final LinkedHashMap<UUID, TablePlayer> players = new LinkedHashMap<>();
    private final List<Block> tableBlocks = new ArrayList<>();

    private Deck deck;
    private final List<Card> board = new ArrayList<>();
    private GameStage stage = GameStage.PRE_FLOP;
    private boolean inHand = false;
    // whether players are allowed to reveal their hand right now
    private boolean revealWindow = false;

    // whose turn is it
    private UUID currentPlayerId = null;
    public boolean hasPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    // Economy Integration
    private BigDecimal pot = BigDecimal.ZERO;
    private BigDecimal currentBet = BigDecimal.ZERO;      // highest bet this street
    private BigDecimal lastRaiseSize = BigDecimal.ZERO;   // size of last raise

    private static final BigDecimal SMALL_BLIND = BigDecimal.valueOf(10);
    private static final BigDecimal BIG_BLIND   = BigDecimal.valueOf(20);

    private EconomyProvider eco() {
        return LetsGambaPlugin.getEconomy();
    }

    private boolean chargePlayer(TablePlayer tp, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return true;

        EconomyProvider eco = eco();
        if (eco == null) {
            Player p = tp.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Economy is not available. Bets are disabled.");
            }
            return false;
        }

        UUID id = tp.getUuid();

        if (!eco.hasEnough(id, amount)) {
            Player p = tp.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.RED + "You don't have enough money to bet " + amount + ".");
            }
            return false;
        }

        if (!eco.subtract(id, amount)) {
            Player p = tp.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.RED + "Economy error – could not take your bet.");
            }
            return false;
        }

        pot = pot.add(amount);
        tp.setBetThisRound(tp.getBetThisRound().add(amount));
        return true;
    }

    private void payWinners(Iterable<TablePlayer> winners) {
        if (pot.compareTo(BigDecimal.ZERO) <= 0) return;

        EconomyProvider eco = eco();
        if (eco == null) return;

        int count = 0;
        for (TablePlayer ignored : winners) {
            count++;
        }
        if (count == 0) return;

        BigDecimal share = pot.divide(BigDecimal.valueOf(count), RoundingMode.DOWN);

        for (TablePlayer tp : winners) {
            eco.add(tp.getUuid(), share);
            Player p = tp.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.GREEN + "You win " + share + " from the pot!");
            }
        }

        pot = BigDecimal.ZERO;
    }

    // Table location + radius
    private Location center;
    private static final double JOIN_RADIUS_MIN = 0.0;
    private static final double JOIN_RADIUS_MAX = 20.0;

    // Dealer / Nitwit Logic
    private UUID dealerId;
    private static final int WOOL_REQUIRED = 6; // Requires exactly 6 blocks for a 3x2 table
    private static final double FIND_RADIUS = 40.0;
    private static final double ACTIVATION_DISTANCE = 5.0; // Distance player must be to start game
    private boolean waitingForHost = false; // Is dealer waiting for player?
    private boolean isSetupInProgress = false;
    private BukkitTask proximityTask = null; // Timer task
    private BukkitTask restartTask = null;

    // Max players at table
    private static final int MAX_PLAYERS = 6;

    public PokerTable(int tableId) {
        this.tableId = tableId;
    }

    public int getTableId() {
        return tableId;
    }

    // NEW: Helper for GSit Listener
    public boolean isBlockPartofTable(Block b) {
        return tableBlocks.contains(b);
    }

    // ---------- BASIC INFO HELPERS ----------

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public int getSeatCount() {
        return players.size();
    }

    public void sendInfo(Player viewer) {
        if (center == null || players.isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "There is currently no active poker table.");
            viewer.sendMessage(ChatColor.GRAY + "Use /poker to open a table with a dealer.");
            return;
        }

        viewer.sendMessage(ChatColor.GOLD + "=== Poker Table Info ===");
        viewer.sendMessage(ChatColor.AQUA + "Center: "
                + ChatColor.YELLOW + center.getWorld().getName()
                + ChatColor.WHITE + " @ "
                + ChatColor.YELLOW + center.getBlockX()
                + ChatColor.WHITE + ", "
                + ChatColor.YELLOW + center.getBlockY()
                + ChatColor.WHITE + ", "
                + ChatColor.YELLOW + center.getBlockZ());

        viewer.sendMessage(ChatColor.AQUA + "Join radius: "
                + ChatColor.YELLOW + (int) JOIN_RADIUS_MIN
                + ChatColor.WHITE + "–"
                + ChatColor.YELLOW + (int) JOIN_RADIUS_MAX
                + ChatColor.WHITE + " blocks.");

        int count = getSeatCount();
        viewer.sendMessage(ChatColor.AQUA + "Players seated: "
                + ChatColor.GOLD + count + ChatColor.AQUA + " / "
                + ChatColor.GOLD + MAX_PLAYERS);

        if (!players.isEmpty()) {
            viewer.sendMessage(ChatColor.AQUA + "Seated players:");
            for (Map.Entry<UUID, TablePlayer> entry : players.entrySet()) {
                TablePlayer tp = entry.getValue();
                Player p = tp.getOnlinePlayer();
                String name = (p != null ? p.getName() : tp.getName());
                boolean isCurrent = (currentPlayerId != null && currentPlayerId.equals(entry.getKey()));
                String turnMark = isCurrent ? ChatColor.GREEN + " (current turn)" : "";
                viewer.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + name + turnMark);
            }
        }

        viewer.sendMessage(ChatColor.AQUA + "Stage: "
                + ChatColor.YELLOW + stage.name()
                + (inHand ? ChatColor.GREEN + " (hand in progress)" : ChatColor.RED + " (no active hand)"));

        // Add pot information
        viewer.sendMessage(ChatColor.AQUA + "Current pot: "
                + ChatColor.GOLD + pot
                + ChatColor.AQUA + ", bet to call: "
                + ChatColor.GOLD + currentBet);
    }

    // ---------- JOIN / LEAVE & RADIUS ----------
    public void hostTable(Player player) { // HOSTING TABLE
        // Optional debug logging (uncomment for troubleshooting)
        // System.out.println("[POKER DEBUG] " + player.getName() + " called hostTable - center: " + center + ", isSetup: " + isSetupInProgress + ", waiting: " + waitingForHost);

        // === EMERGENCY STATE RECOVERY ===
        // Detect and fix corrupted state where flags are true but dealer entity is invalid
        if (isSetupInProgress && (dealerId == null || Bukkit.getEntity(dealerId) == null)) {
            player.sendMessage(ChatColor.YELLOW + "Detected stale setup state (dealer lost). Resetting...");
            isSetupInProgress = false;
            waitingForHost = false;
        }

        // If waiting for host but no valid dealer exists, cleanup everything
        if (waitingForHost && (dealerId == null || Bukkit.getEntity(dealerId) == null)) {
            player.sendMessage(ChatColor.YELLOW + "Previous table setup expired. Resetting...");
            forceCleanup();
        }

        // === ACTIVE TABLE GUARD ===
        // Prevent hosting if a table is already active and not in waiting state
        if (center != null && !waitingForHost) {
            player.sendMessage(ChatColor.RED + "A poker table is already active!");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/poker join" + ChatColor.GRAY + " to join it.");
            return;
        }

        // === SETUP IN PROGRESS GUARD ===
        // Prevent concurrent setup attempts
        if (isSetupInProgress || waitingForHost) {
            if (waitingForHost) {
                player.sendMessage(ChatColor.YELLOW + "The dealer is waiting for the host to approach the table.");
                player.sendMessage(ChatColor.GRAY + "Walk closer to the dealer to activate the table.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "The dealer is walking to the table. Please wait.");
            }
            return;
        }

        // === EXECUTE SETUP WITH FAILSAFE ===
        boolean success = false;
        try {
            success = setupDealerAndTable(player);
        } catch (Exception e) {
            // Catch any unexpected errors during setup
            e.printStackTrace(); // Log to console for debugging
            player.sendMessage(ChatColor.RED + "Unexpected error during table setup. Please try again.");
        } finally {
            // ALWAYS reset flags if setup failed or threw exception
            if (!success) {
                isSetupInProgress = false;
                waitingForHost = false;
            }
        }
    }

    public void join(Player player) {
        // 1. Prevent joining if table isn't set up yet
        if (center == null) {
            if (waitingForHost) {
                player.sendMessage(ChatColor.YELLOW + "The table is waiting for the host to open it.");
                player.sendMessage(ChatColor.GRAY + "Once opened, you can use /poker join.");
                return;
            }
            player.sendMessage(ChatColor.RED + "No poker table is currently open.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/poker" + ChatColor.GRAY + " to host one.");
            return;
        }

        // 2. World Check
        if (!player.getWorld().equals(center.getWorld())) {
            player.sendMessage(ChatColor.RED + "You are in a different world from the poker table.");
            return;
        }

        // 3. Radius Check
        double dist = player.getLocation().distance(center);
        if (dist > JOIN_RADIUS_MAX) {
            player.sendMessage(ChatColor.RED + "You are not in range of the poker table.");
            return;
        }

        // 4. Game State Checks
        if (inHand) {
            player.sendMessage(ChatColor.RED + "A hand is already in progress. Wait for the next round.");
            return;
        }

        if (players.size() >= MAX_PLAYERS) {
            player.sendMessage(ChatColor.RED + "This poker table is full.");
            return;
        }

        // 5. Add Player logic
        UUID id = player.getUniqueId();
        if (!players.containsKey(id)) {
            TablePlayer tp = new TablePlayer(player);
            tp.updateActivity();
            players.put(id, tp);

            broadcast(ChatColor.AQUA + player.getName() + " joined the poker table.");
            updateDealerHead(player);

            if (isEmpty()) startAfkChecker();

            int count = getSeatCount();
            broadcast(ChatColor.GRAY + "Players seated: " + ChatColor.GOLD + count);

            if (players.size() < 2) {
                player.sendMessage(ChatColor.GRAY + "Waiting for at least one more player to join with /poker join.");
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "You are already seated at this table.");
        }
        // Autostart Timer
        if (players.size() >= 2 && !inHand && restartTask == null) {
            broadcast(ChatColor.GREEN + "Minimum players reached. Starting hand in 10 seconds...");

            restartTask = new BukkitRunnable() {
                int countdown = 10;

                @Override
                public void run() {
                    if (countdown > 0) {
                        broadcast(ChatColor.YELLOW + String.valueOf(countdown) + "...");
                        countdown--;
                    } else {
                        restartTask = null;
                        startNewHand(); // Triggers the game start
                        this.cancel();
                    }
                }
            }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 20L); // Run immediately, then every second
        }
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        TablePlayer tp = players.remove(id);

        if (tp == null) {
            player.sendMessage(ChatColor.RED + "You are not seated at this table.");
            return;
        }

        broadcast(ChatColor.YELLOW + player.getName() + " left the poker table.");

        if (inHand && !tp.isFolded()) {
            tp.setFolded(true);
            if (currentPlayerId != null && currentPlayerId.equals(id)) {
                nextTurnOrStage();
            }
        }

        if (inHand && players.size() < 2) {
            broadcast(ChatColor.RED + "Not enough players to continue. Hand ended.");
            endHand();
        }

        if (players.isEmpty()) {
            // NEW: Release dealer logic
            releaseDealer();
            center = null;
        }
        if (players.size() < 2) {
            // If a restart timer was running, cancel it because we lost too many players
            if (restartTask != null) {
                restartTask.cancel();
                restartTask = null;
                broadcast(ChatColor.RED + "Not enough players to continue. Auto-start cancelled.");
            }

            if (inHand) {
                broadcast(ChatColor.RED + "Not enough players to continue. Hand ended.");
                endHand();
            }
        }
        if (players.isEmpty()) {
            forceCleanup(); // Releases the Dealer entity
            PokerManager.removeTable(this.tableId); // Removes from Manager Map
            if (player.isOnline()) {
                player.sendMessage(ChatColor.GRAY + "Table #" + tableId + " has been closed.");
            }
        }
    }

    // NEW: Helper method to clean up without logic checks
    public void forceCleanup() {
        releaseDealer();
        if (proximityTask != null) proximityTask.cancel();
        if (restartTask != null) restartTask.cancel();
        tableBlocks.clear();
        center = null;
    }

    // ---------- START HAND ----------

    private void startNewHand() {
        // Check system
        if (players.size() < 2) {
            broadcast(ChatColor.RED + "Not enough players to start a hand.");
            inHand = false;
            return;
        }

        inHand = true;
        stage = GameStage.PRE_FLOP;
        board.clear();
        deck = new Deck();

        pot = BigDecimal.ZERO;
        currentBet = BigDecimal.ZERO;
        lastRaiseSize = BigDecimal.ZERO;
        currentPlayerId = null;

        for (TablePlayer tp : players.values()) {
            tp.resetForNewHand();
            Card[] hand = tp.getHand();
            hand[0] = deck.draw();
            hand[1] = deck.draw();

            Player p = tp.getOnlinePlayer();
            if (p != null && p.isOnline()) {
                p.sendMessage(ChatColor.AQUA + "New multiplayer PvP hand started!");
                p.sendMessage(ChatColor.AQUA + "Your hand: "
                        + cardText(hand[0]) + ChatColor.AQUA + " and " + cardText(hand[1]));
            }
        }

        broadcast(ChatColor.GREEN + "Pre-flop: players act in turn.");

        // Apply blinds
        applyBlinds();

        List<TablePlayer> active = getActivePlayersInOrder();
        if (!active.isEmpty()) {
            int n = active.size();
            int startIndex;

            if (n >= 3) {
                startIndex = 2 % n; // player after big blind
            } else if (n == 2) {
                startIndex = 0;     // heads-up: SB acts first
            } else {
                startIndex = 0;
            }

            currentPlayerId = active.get(startIndex).getUuid();
            announceTurn();
        }
    }
    // Blind Logic
    private void applyBlinds() {
        List<TablePlayer> active = getActivePlayersInOrder();
        if (active.size() < 2) return;

        TablePlayer small = active.get(0);
        TablePlayer big   = active.get(1);

        if (chargePlayer(small, SMALL_BLIND)) {
            Player p = small.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.YELLOW + "You post the small blind of " + SMALL_BLIND + ".");
            }
        }

        if (chargePlayer(big, BIG_BLIND)) {
            Player p = big.getOnlinePlayer();
            if (p != null) {
                p.sendMessage(ChatColor.YELLOW + "You post the big blind of " + BIG_BLIND + ".");
            }
        }

        currentBet = BIG_BLIND;
        lastRaiseSize = BIG_BLIND;

        broadcast(ChatColor.GRAY + "Blinds posted: "
                + ChatColor.YELLOW + small.getName() + ChatColor.GRAY + " (SB "
                + SMALL_BLIND + "), "
                + ChatColor.YELLOW + big.getName() + ChatColor.GRAY + " (BB "
                + BIG_BLIND + ").");
        broadcast(ChatColor.AQUA + "Current pot: " + ChatColor.GOLD + pot
                + ChatColor.AQUA + ", bet to call: " + ChatColor.GOLD + currentBet);
    }

    // ---------- ACTION HANDLING (NO ECONOMY) ----------

    public void handleAction(Player player, PokerAction action) {
        if (!inHand) {
            player.sendMessage(ChatColor.RED + "No active hand. Use /poker to start or join.");
            return;
        }

        TablePlayer tp = players.get(player.getUniqueId());
        if (tp == null) {
            player.sendMessage(ChatColor.RED + "You are not seated at this table. Use /poker to join.");
            return;
        }

        if (tp.isFolded()) {
            player.sendMessage(ChatColor.RED + "You already folded this hand.");
            return;
        }

        if (!player.getUniqueId().equals(currentPlayerId)) {
            player.sendMessage(ChatColor.RED + "It is not your turn.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        tp.updateActivity();

        BigDecimal playerBet = tp.getBetThisRound();

        switch (action) {
            case FOLD -> {
                tp.setFolded(true);
                tp.setActedThisStreet(true);
                broadcast(ChatColor.RED + player.getName() + " folds.");
                nextTurnOrStage();
            }

            case CHECK_OR_CALL -> {
                BigDecimal toCall = currentBet.subtract(playerBet);

                if (toCall.compareTo(BigDecimal.ZERO) <= 0) {
                    tp.setActedThisStreet(true);
                    broadcast(ChatColor.YELLOW + player.getName() + " checks.");
                    nextTurnOrStage();
                } else {
                    if (!chargePlayer(tp, toCall)) {
                        player.sendMessage(ChatColor.RED + "You cannot call and have been folded.");
                        tp.setFolded(true);
                        tp.setActedThisStreet(true);
                        nextTurnOrStage();
                    } else {
                        tp.setActedThisStreet(true);
                        broadcast(ChatColor.YELLOW + player.getName() + " calls " + toCall + ".");
                        nextTurnOrStage();
                    }
                }
            }

            case RAISE -> {
                BigDecimal minRaise = lastRaiseSize.max(BIG_BLIND);
                BigDecimal newBet   = currentBet.add(minRaise);
                BigDecimal toPay    = newBet.subtract(playerBet);

                if (!chargePlayer(tp, toPay)) {
                    // Just warn, do NOT fold, do NOT advance turn
                    player.sendMessage(ChatColor.RED + "You cannot afford that raise.");

                    // Re-show the action buttons so they can choose fold / call / all-in, etc.
                    gambaCommand cmd = (gambaCommand) LetsGambaPlugin
                            .getInstance()
                            .getCommand("poker")
                            .getExecutor();

                    if (cmd != null) {
                        cmd.sendActionMenu(player);
                    }

                    return; // still this player's turn
                }{
                    currentBet = newBet;
                    lastRaiseSize = minRaise;
                    tp.setActedThisStreet(true);
                    broadcast(ChatColor.GREEN + player.getName() + " raises to " + currentBet + ".");
                    nextTurnOrStage();
                }
            }
        }
    }

    //--- all in ---
    public void handleAllIn(Player player) {

        if (!inHand) {
            player.sendMessage(ChatColor.RED + "No active hand. Use /poker to start or join.");
            return;
        }
        TablePlayer tp = players.get(player.getUniqueId());
        if (tp == null || tp.isFolded()) {
            player.sendMessage(ChatColor.RED + "You are not in this hand.");
            return;
        }
        if (tp.isAllIn()) {
            player.sendMessage(ChatColor.RED + "You are already all-in.");
            return;
        }
        if (!player.getUniqueId().equals(currentPlayerId)) {
            player.sendMessage(ChatColor.RED + "It is not your turn.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        tp.updateActivity();
        EconomyProvider eco = eco();
        if (eco == null) {
            player.sendMessage(ChatColor.RED + "Economy is not available.");
            return;
        }

        UUID id = tp.getUuid();
        // How much money does the player have in Vault/Essentials?
        BigDecimal wallet = eco.getBalance(id);
        if (wallet == null || wallet.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(ChatColor.RED + "You have no money to go all-in with.");
            return;
        }
        BigDecimal playerBet = tp.getBetThisRound();
        BigDecimal amountToBet = wallet; // bet entire wallet for now

        // Take the money + add to pot using existing chargePlayer()
        if (!chargePlayer(tp, amountToBet)) {
            player.sendMessage(ChatColor.RED + "Economy error – could not go all-in.");
            return;
        }

        tp.setAllIn(true);

        BigDecimal newTotalBet = playerBet.add(amountToBet);

        // If this is higher than currentBet, it's a raise
        if (newTotalBet.compareTo(currentBet) > 0) {
            lastRaiseSize = newTotalBet.subtract(currentBet);
            currentBet = newTotalBet;

            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN +
                    " goes ALL IN for " + ChatColor.GOLD + newTotalBet + ChatColor.GREEN + "!");
        } else {
            // All-in just to call / match
            broadcast(ChatColor.GOLD + player.getName() + ChatColor.GREEN +
                    " goes ALL IN for " + ChatColor.GOLD + newTotalBet +
                    ChatColor.GREEN + " (call).");
        }
        tp.setActedThisStreet(true);
        nextTurnOrStage();
    }

    // ---------- TURN / STREET LOGIC ----------

    // Add handleCustomRaise method
    public void handleCustomRaise(Player player, BigDecimal raiseToTotal) {
        if (!inHand) {
            player.sendMessage(ChatColor.RED + "No active hand. Use /poker to start or join.");
            return;
        }

        TablePlayer tp = players.get(player.getUniqueId());
        if (tp == null || tp.isFolded()) {
            player.sendMessage(ChatColor.RED + "You are not in this hand.");
            return;
        }

        if (!player.getUniqueId().equals(currentPlayerId)) {
            player.sendMessage(ChatColor.RED + "It is not your turn.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        tp.updateActivity();

        BigDecimal playerBet = tp.getBetThisRound();

        BigDecimal minTotal = currentBet.add(lastRaiseSize.max(BIG_BLIND));
        if (raiseToTotal.compareTo(minTotal) < 0) {
            player.sendMessage(ChatColor.RED + "Your raise must be at least the minimum raise above the current bet.");
            return;
        }

        BigDecimal toPay = raiseToTotal.subtract(playerBet);
        if (toPay.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(ChatColor.RED + "You are already at or above that amount.");
            return;
        }

        if (!chargePlayer(tp, toPay)) {
            player.sendMessage(ChatColor.RED + "You cannot afford that raise. Treated as fold.");
            tp.setFolded(true);
            tp.setActedThisStreet(true);
            nextTurnOrStage();
            return;
        }

        lastRaiseSize = raiseToTotal.subtract(currentBet);
        currentBet = raiseToTotal;
        tp.setActedThisStreet(true);

        broadcast(ChatColor.GREEN + player.getName() + " raises to " + currentBet + " (custom).");
        nextTurnOrStage();
    }

    private void nextTurnOrStage() {
        List<TablePlayer> active = getActivePlayersInOrder();

        if (active.size() <= 1) {
            if (active.size() == 1) {
                TablePlayer winner = active.get(0);
                broadcast(ChatColor.GREEN + winner.getName()
                        + " wins the pot (everyone else folded).");
                payWinners(Collections.singletonList(winner));
                Player wp = winner.getOnlinePlayer();
                if (wp != null) {
                    wp.playSound(wp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
            } else {
                broadcast(ChatColor.RED + "No active players remain.");
            }
            Bukkit.getScheduler().runTaskLater(
                    LetsGambaPlugin.getInstance(),
                    this::endHand,
                    140L // 100 ticks ≈ 5 seconds (adjust if you like)
            );
            return;
        }

        boolean allActed = true;
        for (TablePlayer tp : active) {
            if (!tp.hasActedThisStreet()) {
                allActed = false;
                break;
            }
        }

        if (!allActed) {
            TablePlayer next = getNextActivePlayerWhoHasNotActed(active);
            if (next != null) {
                currentPlayerId = next.getUuid();
                announceTurn();
                return;
            }
        }

        for (TablePlayer tp : active) {
            tp.resetForNewStreet();
        }

        advanceStage();
    }

    private void advanceStage() {
        switch (stage) {
            case PRE_FLOP -> {
                board.add(deck.draw());
                board.add(deck.draw());
                board.add(deck.draw());
                stage = GameStage.FLOP;

                broadcast(ChatColor.GREEN + "Flop is dealt:");
                broadcastBoard();
                remindAllPlayersOfHands();
            }
            case FLOP -> {
                board.add(deck.draw());
                board.add(deck.draw());
                stage = GameStage.RIVER;

                broadcast(ChatColor.GREEN + "Turn and River dealt:");
                broadcastBoard();
                remindAllPlayersOfHands();
            }
            case RIVER -> {
                stage = GameStage.SHOWDOWN;
                doShowdownPvP();

                // enable 4-second window to show hands
                revealWindow = true;
                broadcast(ChatColor.GRAY + "You have " + ChatColor.GOLD + "4 seconds" +
                        ChatColor.GRAY + " to type " + ChatColor.YELLOW + "/poker showhand" +
                        ChatColor.GRAY + " if you want to reveal your cards.");
                for (TablePlayer tp : players.values()) {
                    Player p = tp.getOnlinePlayer();
                    if (p == null) continue;

                    TextComponent base = new TextComponent(ChatColor.GOLD + "[SHOW HAND]");
                    base.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/poker showhand"));
                    base.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder(ChatColor.YELLOW + "Reveal your cards to the table").create()));

                    p.spigot().sendMessage(base);
                }
                // ⏱ wait 4 seconds before ending the hand
                Bukkit.getScheduler().runTaskLater(LetsGambaPlugin.getInstance(), () -> {
                    endHand();
                }, 140L); // 20 ticks = 1 second → 80 ticks = 4 seconds
                return;
            }
            default -> {}
        }

        List<TablePlayer> active = getActivePlayersInOrder();
        if (!active.isEmpty()) {
            currentPlayerId = active.get(0).getUuid();
            announceTurn();
        }
    }

    //--- show hand ---
    public void requestShowHand(Player player) {
        if (!inHand) {
            player.sendMessage(ChatColor.RED + "There is no active hand.");
            return;
        }
        if (!revealWindow) {
            player.sendMessage(ChatColor.RED + "You can only show your hand right after the pot is awarded.");
            return;
        }

        TablePlayer tp = players.get(player.getUniqueId());
        if (tp == null) {
            player.sendMessage(ChatColor.RED + "You are not seated at this table.");
            return;
        }

        Card[] hand = tp.getHand();
        if (hand == null || hand.length < 2) {
            player.sendMessage(ChatColor.RED + "You have no cards to show.");
            return;
        }

        // broadcast to everyone at the table
        broadcast(ChatColor.GOLD + player.getName() + ChatColor.GRAY +
                " shows " + ChatColor.AQUA + cardText(hand[0]) +
                ChatColor.GRAY + " and " + ChatColor.AQUA + cardText(hand[1]) + ChatColor.GRAY + ".");
    }


    // ---------- SHOWDOWN ----------

    private void doShowdownPvP() {
        broadcast(ChatColor.LIGHT_PURPLE + "Showdown! PvP – best hand wins.");
        broadcastBoard();

        Map<TablePlayer, HandValue> values = new LinkedHashMap<>();

        for (TablePlayer tp : players.values()) {
            if (tp.isFolded()) {
                Player p = tp.getOnlinePlayer();
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.DARK_GRAY + "You folded earlier this hand and are not in the pot.");
                }
                continue;
            }

            Player p = tp.getOnlinePlayer();
            if (p == null || !p.isOnline()) {
                continue;
            }

            List<Card> cards = new ArrayList<>();
            Card[] hand = tp.getHand();
            cards.add(hand[0]);
            cards.add(hand[1]);
            cards.addAll(board);

            HandValue hv = HandEvaluator.evaluateSeven(cards);
            values.put(tp, hv);

            p.sendMessage(ChatColor.AQUA + "Your hand: "
                    + cardText(hand[0]) + ChatColor.AQUA + " and " + cardText(hand[1]));
            p.sendMessage(ChatColor.GREEN + "Your best hand: " + ChatColor.GOLD + hv.getReadableName());
        }

        if (values.isEmpty()) {
            broadcast(ChatColor.RED + "Nobody was eligible for showdown.");
            return;
        }

        HandValue best = null;
        List<TablePlayer> winners = new ArrayList<>();

        for (Map.Entry<TablePlayer, HandValue> entry : values.entrySet()) {
            TablePlayer tp = entry.getKey();
            HandValue hv = entry.getValue();

            if (best == null || hv.compareTo(best) > 0) {
                best = hv;
                winners.clear();
                winners.add(tp);
            } else if (hv.compareTo(best) == 0) {
                winners.add(tp);
            }
        }

        String winnersNames = buildWinnersNames(winners);
        String handName = (best != null) ? best.getReadableName() : "Unknown hand";

        broadcast(ChatColor.GOLD + winnersNames
                + ChatColor.GREEN + (winners.size() == 1 ? " wins " : " win ")
                + "the pot with "
                + ChatColor.GOLD + handName + ChatColor.GREEN + "!");

        // Pay winners
        payWinners(winners);

        for (TablePlayer tp : players.values()) {
            Player p = tp.getOnlinePlayer();
            if (p == null || !p.isOnline()) continue;

            if (tp.isFolded()) {
                p.sendMessage(ChatColor.DARK_GRAY + "You do not win the pot because you folded.");
                p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
            } else if (winners.contains(tp)) {
                if (winners.size() == 1) {
                    p.sendMessage(ChatColor.GREEN + "You win the pot!");
                } else {
                    p.sendMessage(ChatColor.GREEN + "You tie for the pot with other players.");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.sendMessage(ChatColor.RED + "You lose this hand. Pot goes to " + winnersNames + ".");
                p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
            }
        }
    }

    private String buildWinnersNames(List<TablePlayer> winners) {
        if (winners.isEmpty()) return "Nobody";
        if (winners.size() == 1) return winners.get(0).getName();
        if (winners.size() == 2) {
            return winners.get(0).getName() + " and " + winners.get(1).getName();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < winners.size(); i++) {
            if (i > 0) {
                if (i == winners.size() - 1) sb.append(" and ");
                else sb.append(", ");
            }
            sb.append(winners.get(i).getName());
        }
        return sb.toString();
    }

    private void endHand() {
        inHand = false;
        stage = GameStage.FINISHED;
        currentPlayerId = null;

        // Reset betting variables
        pot = BigDecimal.ZERO;
        currentBet = BigDecimal.ZERO;
        lastRaiseSize = BigDecimal.ZERO;

        int playerCount = players.size();

        if (playerCount < 2) {
            broadcast(ChatColor.RED + "Waiting for more players to join to start the next hand...");
            broadcast(ChatColor.GRAY + "Current players: " + playerCount + "/6. Use /poker join.");
        } else {
            broadcast(ChatColor.GRAY + "--------------------------------");
            broadcast(ChatColor.AQUA + "  Hand Finished!");
            broadcast(ChatColor.YELLOW + "  Next hand starting in 5 seconds...");
            broadcast(ChatColor.GRAY + "  Type " + ChatColor.RED + "/poker leave" + ChatColor.GRAY + " to quit now.");
            broadcast(ChatColor.GRAY + "--------------------------------");

            // Assign to variable so we can check it later
            restartTask = new BukkitRunnable() {
                @Override
                public void run() {
                    restartTask = null; // Clear the variable when it runs

                    // Safety checks
                    if (players.size() < 2) {
                        broadcast(ChatColor.RED + "Not enough players to start the next hand.");
                        return;
                    }
                    if (center == null) return;

                    startNewHand();
                }
            }.runTaskLater(LetsGambaPlugin.getInstance(), 100L); // 5 seconds
        }
    }

    // ---------- TURN HELPERS ----------

    private List<TablePlayer> getActivePlayersInOrder() {
        List<TablePlayer> active = new ArrayList<>();
        for (TablePlayer tp : players.values()) {
            if (!tp.isFolded()) {
                active.add(tp);
            }
        }
        return active;
    }

    private TablePlayer getNextActivePlayerWhoHasNotActed(List<TablePlayer> active) {
        if (active.isEmpty()) return null;

        List<TablePlayer> ordered = getActivePlayersInOrder();
        int startIndex = 0;

        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getUuid().equals(currentPlayerId)) {
                startIndex = i;
                break;
            }
        }

        int n = ordered.size();
        for (int offset = 1; offset <= n; offset++) {
            TablePlayer candidate = ordered.get((startIndex + offset) % n);
            if (!candidate.hasActedThisStreet() && !candidate.isFolded()) {
                return candidate;
            }
        }
        return null;
    }

    private void updateDealerHead(Player target) {
        if (dealerId == null || target == null) return;

        Entity e = Bukkit.getEntity(dealerId);
        if (!(e instanceof Villager)) return;
        Villager dealer = (Villager) e;

        Location dealerLoc = dealer.getLocation();
        Location targetLoc = target.getEyeLocation();

        // Calculate vector from Dealer Eyes to Player Eyes
        Vector direction = targetLoc.toVector().subtract(dealerLoc.clone().add(0, 1.62, 0).toVector());

        // Update the location direction so he faces the player
        dealerLoc.setDirection(direction);

        // Teleport in place (Required because we set AI to false later)
        dealer.teleport(dealerLoc);
    }

    private void announceTurn() {
        if (currentPlayerId == null) return;

        TablePlayer tp = players.get(currentPlayerId);
        if (tp == null) return;

        tp.updateActivity();

        Player p = tp.getOnlinePlayer();
        if (p == null || !p.isOnline()) return;

        broadcast(ChatColor.AQUA + "It is now " + tp.getName() + "'s turn.");

        updateDealerHead(p);
        Card[] hand = tp.getHand();
        p.sendMessage(ChatColor.AQUA + "Your hand: "
                + cardText(hand[0]) + ChatColor.AQUA + " and " + cardText(hand[1]));

        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);

        sendChatButtons(p);
    }

    private void sendChatButtons(Player player) {
        gambaCommand cmd = (gambaCommand) LetsGambaPlugin.getInstance()
                .getCommand("poker")
                .getExecutor();

        if (cmd != null) {
            cmd.sendActionMenu(player);
        }
    }


    // ---------- BROADCAST HELPERS ----------

    private void broadcast(String msg) {
        for (TablePlayer tp : players.values()) {
            Player p = tp.getOnlinePlayer();
            if (p != null && p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void broadcastBoard() {
        StringBuilder sb = new StringBuilder(ChatColor.YELLOW + "Board: ");
        for (Card c : board) {
            sb.append(ChatColor.GOLD)
                    .append("[")
                    .append(cardText(c))
                    .append("] ");
        }
        broadcast(sb.toString());
    }

    // ---------- CARD DISPLAY ----------

    private String cardText(Card c) {
        String suitSymbol;
        switch (c.getSuit()) {
            case CLUBS -> suitSymbol = ChatColor.DARK_GRAY + "♣";
            case SPADES -> suitSymbol = ChatColor.DARK_GRAY + "♠";
            case HEARTS -> suitSymbol = ChatColor.RED + "♥";
            case DIAMONDS -> suitSymbol = ChatColor.RED + "♦";
            default -> suitSymbol = "?";
        }

        String rankSymbol;
        switch (c.getRank()) {
            case TWO -> rankSymbol = "2";
            case THREE -> rankSymbol = "3";
            case FOUR -> rankSymbol = "4";
            case FIVE -> rankSymbol = "5";
            case SIX -> rankSymbol = "6";
            case SEVEN -> rankSymbol = "7";
            case EIGHT -> rankSymbol = "8";
            case NINE -> rankSymbol = "9";
            case TEN -> rankSymbol = "10";
            case JACK -> rankSymbol = "J";
            case QUEEN -> rankSymbol = "Q";
            case KING -> rankSymbol = "K";
            case ACE -> rankSymbol = "A";
            default -> rankSymbol = "?";
        }

        return suitSymbol + ChatColor.WHITE + " " + rankSymbol;
    }

    private void remindAllPlayersOfHands() {
        for (TablePlayer tp : players.values()) {
            if (tp.isFolded() || tp.isAllIn()) continue;

            Player p = tp.getOnlinePlayer();
            if (p == null || !p.isOnline()) continue;

            Card[] hand = tp.getHand();
            p.sendMessage(ChatColor.GRAY + "Your hand: "
                    + cardText(hand[0]) + ChatColor.GRAY + " and " + cardText(hand[1]));
        }
    }

    // ---------- DEALER / NITWIT LOGIC ----------
    boolean setupDealerAndTable(Player player) {
        isSetupInProgress = true;

        // 1. Find Green Wool nearby
        List<Block> woolBlocks = findNearbyGreenWool(player.getLocation());
        if (woolBlocks.size() < WOOL_REQUIRED) {
            player.sendMessage(ChatColor.RED + "Not enough Green Wool nearby!");
            player.sendMessage(ChatColor.GRAY + "You need a 3x2 Green Wool table.");
            isSetupInProgress = false;
            return false;
        }
        this.tableBlocks.clear();
        this.tableBlocks.addAll(woolBlocks);

        Location tableCenter = getCentroid(woolBlocks);

        // 2. Find Nitwit (Green Villager)
        Villager nitwit = findNearbyNitwit(player.getLocation());
        if (nitwit == null) {
            player.sendMessage(ChatColor.RED + "No Nitwit found nearby.");
            isSetupInProgress = false;
            return false;
        }

        // 3. Calculate Dealer Destination
        // (Logic copied from previous file to ensure we get the target location BEFORE moving him)
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Block b : woolBlocks) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }

        double midX = (minX + maxX) / 2.0 + 0.5;
        double midZ = (minZ + maxZ) / 2.0 + 0.5;
        double dealerY = tableCenter.getY() - 1.0;

        Location c1, c2;
        int xLen = maxX - minX;
        int zLen = maxZ - minZ;

        if (xLen > zLen) {
            c1 = new Location(tableCenter.getWorld(), midX, dealerY, minZ - 0.5);
            c2 = new Location(tableCenter.getWorld(), midX, dealerY, maxZ + 1.5);
        } else {
            c1 = new Location(tableCenter.getWorld(), minX - 0.5, dealerY, midZ);
            c2 = new Location(tableCenter.getWorld(), maxX + 1.5, dealerY, midZ);
        }

        boolean c1HasStairs = hasNeighboringStairs(c1, tableCenter);
        boolean c2HasStairs = hasNeighboringStairs(c2, tableCenter);

        Location targetLoc;
        // FIXED: Prioritize the side WITHOUT stairs
        if (c1HasStairs && !c2HasStairs) {
            targetLoc = c2; // c2 is clear, use it
        } else if (!c1HasStairs && c2HasStairs) {
            targetLoc = c1; // c1 is clear, use it
        } else if (!c1HasStairs && !c2HasStairs) {
            // Both sides are clear, choose closer one
            targetLoc = (player.getLocation().distanceSquared(c1) < player.getLocation().distanceSquared(c2)) ? c1 : c2;
        } else {
            // Both have stairs (worst case), choose closer one and warn
            targetLoc = (player.getLocation().distanceSquared(c1) < player.getLocation().distanceSquared(c2)) ? c1 : c2;
            player.sendMessage(ChatColor.YELLOW + "Warning: Both dealer positions have stairs nearby!");
        }

        // 4. Initialize Game Data
        this.dealerId = nitwit.getUniqueId();
        this.center = tableCenter;

        // 5. BUG FIX: Check if Dealer is ALREADY there
        if (nitwit.getLocation().distance(targetLoc) < 1.5) {
            player.sendMessage(ChatColor.GREEN + "Dealer is already at the table.");

            // Ensure he is snapped to grid and frozen
            targetLoc.setDirection(tableCenter.toVector().subtract(targetLoc.toVector()));
            targetLoc.setPitch(0);
            nitwit.teleport(targetLoc);
            nitwit.setAI(false);
            nitwit.setInvulnerable(true);
            nitwit.setGravity(true);

            // Skip walking, go straight to waiting
            isSetupInProgress = false;
            startProximityCheck(player);
            return true;
        }

        // 6. Start Dealer Movement Routine
        startDealerWalkingRoutine(player, nitwit, targetLoc, tableCenter);
        return true;
    }

    // Update this method to set isSetupInProgress = false when done or cancelled
    private void startDealerWalkingRoutine(Player player, Villager dealer, Location targetPos, Location lookAtTarget) {
        player.sendMessage(ChatColor.GREEN + "The Nitwit is walking to the dealer's seat...");
        dealer.setAI(true);
        dealer.setTarget(null);
        if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);

        new BukkitRunnable() {
            int ticks = 0;
            final int MAX_WAIT_TICKS = 100;

            @Override
            public void run() {
                if (dealerId == null || !dealer.isValid()) {
                    isSetupInProgress = false;
                    this.cancel();
                    return;
                }

                double dist = dealer.getLocation().distance(targetPos);

                if (dist < 1.2 || ticks >= MAX_WAIT_TICKS) {
                    Location finalLoc = targetPos.clone();
                    Vector dir = lookAtTarget.clone().toVector().subtract(finalLoc.toVector());
                    finalLoc.setDirection(dir);
                    finalLoc.setPitch(0);

                    dealer.teleport(finalLoc);
                    dealer.setAI(false);
                    dealer.setInvulnerable(true);
                    dealer.setGravity(true);

                    isSetupInProgress = false;
                    startProximityCheck(player);
                    this.cancel();
                    return;
                }

                if (ticks % 20 == 0 && ticks < MAX_WAIT_TICKS) {
                    if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);
                }
                ticks += 5;
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 5L);
    }

    private boolean hasNeighboringStairs(Location dealerLoc, Location faceTarget) {
        // Calculate the vector pointing from Dealer to Table Center
        Vector direction = faceTarget.toVector().subtract(dealerLoc.toVector()).setY(0).normalize();

        // Calculate Right Vector (Cross product with Up)
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        // Calculate Left Vector (Reverse of Right)
        Vector left = right.clone().multiply(-1);

        // Get blocks to left and right
        Block rightBlock = dealerLoc.clone().add(right).getBlock();
        Block leftBlock = dealerLoc.clone().add(left).getBlock();

        return isStair(rightBlock) || isStair(leftBlock);
    }

    private boolean isStair(Block b) {
        return b != null && b.getType().name().endsWith("_STAIRS");
    }

    private void startProximityCheck(Player initiator) {
        waitingForHost = true;
        initiator.sendMessage(ChatColor.GREEN + "The Dealer has arrived! Walk to the table to open it.");

        // Task runs every 1 second (20 ticks)
        proximityTask = new BukkitRunnable() {
            int secondsWaited = 0;
            final int TIMEOUT = 45;

            @Override
            public void run() {
                // Safety check
                if (dealerId == null || center == null) {
                    this.cancel();
                    waitingForHost = false;
                    return;
                }

                // Check if initiator (or any player) is close enough
                boolean playerClose = false;
                if (initiator.isOnline() && initiator.getWorld().equals(center.getWorld())) {
                    if (initiator.getLocation().distance(center) <= ACTIVATION_DISTANCE) {
                        playerClose = true;
                    }
                }

                if (playerClose) {
                    waitingForHost = false;

                    Bukkit.broadcastMessage(ChatColor.GREEN + "[Poker] A table is open at "
                            + ChatColor.YELLOW + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                            + ChatColor.GREEN + "! Use " + ChatColor.GOLD + "/poker join" + ChatColor.GREEN + " to join the table.");

                    // Manually trigger join for the initiator now that they are close
                    join(initiator);

                    this.cancel();
                    return;
                }

                secondsWaited++;

                // Timeout logic
                if (secondsWaited >= TIMEOUT) {
                    if (initiator.isOnline()) {
                        initiator.sendMessage(ChatColor.RED + "Poker request timed out. You didn't approach the table.");
                    }
                    releaseDealer(); // Reset everything
                    center = null;
                    waitingForHost = false;
                    this.cancel();
                }
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 20L);
    }

    private void releaseDealer() {
        if (proximityTask != null && !proximityTask.isCancelled()) {
            proximityTask.cancel();
        }
        waitingForHost = false;

        if (dealerId == null) return;

        Entity e = Bukkit.getEntity(dealerId);
        if (e instanceof Villager) {
            Villager v = (Villager) e;
            // Restore normal behavior
            v.setAI(true);
            v.setInvulnerable(false);
        }
        dealerId = null;
    }

    private List<Block> findNearbyGreenWool(Location center) {
        List<Block> blocks = new ArrayList<>();
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.getBlock().getRelative(x, y, z);
                    if (b.getType() == Material.GREEN_WOOL) {
                        blocks.add(b);
                    }
                }
            }
        }
        return blocks;
    }

    private Villager findNearbyNitwit(Location loc) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, FIND_RADIUS, FIND_RADIUS, FIND_RADIUS)) {
            if (e instanceof Villager) {
                Villager v = (Villager) e;
                if (v.getProfession() == Profession.NITWIT && v.isValid()) {
                    return v;
                }
            }
        }
        return null;
    }

    private Location getCentroid(List<Block> blocks) {
        if (blocks.isEmpty()) return null;
        double totalX = 0, totalY = 0, totalZ = 0;
        for (Block b : blocks) {
            totalX += b.getX() + 0.5;
            totalY += b.getY() + 0.5;
            totalZ += b.getZ() + 0.5;
        }
        return new Location(blocks.get(0).getWorld(), totalX / blocks.size(), totalY / blocks.size(), totalZ / blocks.size());
    }

    public Location getCenter() {
        return center;
    }

    public boolean isLocationOverlapping(Location otherCenter) {
        if (this.center == null || otherCenter == null) return false;
        if (!this.center.getWorld().equals(otherCenter.getWorld())) return false;
        return this.center.distance(otherCenter) < 6.0;
    }

    // ---------- AFK CHECKER ----------

    public void startAfkChecker() {
        Bukkit.getScheduler().runTaskTimer(LetsGambaPlugin.getInstance(), () -> {
            if (players.isEmpty() || center == null) return;

            long now = System.currentTimeMillis();

            // Use a copy of values to avoid ConcurrentModificationException when removing players
            for (TablePlayer tp : new ArrayList<>(players.values())) {
                Player p = tp.getOnlinePlayer();

                // 1. Check if player is offline
                if (p == null || !p.isOnline()) {
                    leave(tp.getOnlinePlayer()); // Use cached player object or UUID logic in leave
                    continue;
                }

                // --- NEW DISTANCE CHECK ---
                if (!p.getWorld().equals(center.getWorld()) || p.getLocation().distance(center) > 10.0) {
                    p.sendMessage(ChatColor.RED + "You moved too far from the table and have left the game.");
                    leave(p);
                    continue;
                }
                // --------------------------

                // 2. Existing Time Check
                long inactiveMs = now - tp.getLastActionTime();

                // 1:30 to 3:00 → send warning ONCE
                if (inactiveMs >= 90_000 && inactiveMs < 180_000) {
                    if (!tp.isAfkWarned()) {
                        p.sendMessage(ChatColor.YELLOW + "You have been inactive for 1 minute 30 seconds.");
                        p.sendMessage(ChatColor.YELLOW + "You will be kicked from the poker table if inactive for 3 minutes.");
                        tp.setAfkWarned(true);
                    }
                }

                // 3:00+ → kick
                if (inactiveMs >= 180_000) {
                    p.sendMessage(ChatColor.RED + "You have been kicked for being AFK.");
                    leave(p);
                }
            }
        }, 20L, 20L); // Checks every 1 second (20 ticks)
    }
}