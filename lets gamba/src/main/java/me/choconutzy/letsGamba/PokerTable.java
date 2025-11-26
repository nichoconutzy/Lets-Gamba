package me.choconutzy.letsGamba;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class PokerTable {

    // Seating order
    private final LinkedHashMap<UUID, TablePlayer> players = new LinkedHashMap<>();

    private Deck deck;
    private final List<Card> board = new ArrayList<>();
    private GameStage stage = GameStage.PRE_FLOP;
    private boolean inHand = false;

    // whose turn is it
    private UUID currentPlayerId = null;

    // Table location + radius
    private Location center;
    private static final double JOIN_RADIUS_MIN = 0.0;
    private static final double JOIN_RADIUS_MAX = 20.0;

    // Max players at table
    private static final int MAX_PLAYERS = 6;

    public PokerTable() {
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
            viewer.sendMessage(ChatColor.GRAY + "Use /poker to open one.");
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
    }

    // ---------- JOIN / LEAVE & RADIUS ----------

    public void join(Player player) {

        // radius / center handling
        if (isEmpty()) {
            center = player.getLocation().clone();
        } else {
            if (center == null) {
                center = player.getLocation().clone();
            }
            if (!player.getWorld().equals(center.getWorld())) {
                player.sendMessage(ChatColor.RED + "You are in a different world from the poker table.");
                return;
            }
            double dist = player.getLocation().distance(center);
            if (dist < JOIN_RADIUS_MIN || dist > JOIN_RADIUS_MAX) {
                player.sendMessage(ChatColor.RED + "You are not in range of the poker table.");
                player.sendMessage(ChatColor.GRAY + "You must be between "
                        + (int) JOIN_RADIUS_MIN + " and " + (int) JOIN_RADIUS_MAX
                        + " blocks from where the table was opened.");
                return;
            }
        }

        if (inHand) {
            player.sendMessage(ChatColor.RED + "A hand is already in progress. Wait for the next round.");
            return;
        }

        if (players.size() >= MAX_PLAYERS) {
            player.sendMessage(ChatColor.RED + "This poker table is full. (" + MAX_PLAYERS + " players maximum)");
            return;
        }

        UUID id = player.getUniqueId();
        if (!players.containsKey(id)) {
            TablePlayer tp = new TablePlayer(player);
            tp.updateActivity();
            players.put(id, tp);

            broadcast(ChatColor.AQUA + player.getName() + " joined the poker table.");

            int count = getSeatCount();
            broadcast(ChatColor.GRAY + "There "
                    + (count == 1 ? "is " : "are ")
                    + ChatColor.GOLD + count + ChatColor.GRAY
                    + (count == 1 ? " player" : " players")
                    + " seated at the table.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You are already seated at this table.");
        }

        if (players.size() >= 2 && !inHand) {
            startNewHand();
        } else if (players.size() < 2) {
            player.sendMessage(ChatColor.GRAY + "Waiting for at least one more player to join with /poker.");
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
            center = null;
        }
    }

    // ---------- START HAND ----------

    private void startNewHand() {
        inHand = true;
        stage = GameStage.PRE_FLOP;
        board.clear();
        deck = new Deck();
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

        broadcast(ChatColor.GREEN + "Pre-flop: players act in turn. Best hand wins the pot!");

        List<TablePlayer> active = getActivePlayersInOrder();
        if (!active.isEmpty()) {
            currentPlayerId = active.get(0).getUuid();
            announceTurn();
        }
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

        switch (action) {
            case FOLD -> {
                tp.setFolded(true);
                tp.setActedThisStreet(true);
                broadcast(ChatColor.RED + player.getName() + " folds.");
            }
            case CHECK_OR_CALL -> {
                tp.setActedThisStreet(true);
                broadcast(ChatColor.YELLOW + player.getName() + " checks/calls.");
            }
            case RAISE -> {
                tp.setActedThisStreet(true);
                broadcast(ChatColor.GREEN + player.getName() + " raises (abstract, no chips yet).");
            }
        }

        nextTurnOrStage();
    }

    // ---------- TURN / STREET LOGIC ----------

    private void nextTurnOrStage() {
        List<TablePlayer> active = getActivePlayersInOrder();

        if (active.size() <= 1) {
            if (active.size() == 1) {
                TablePlayer winner = active.get(0);
                broadcast(ChatColor.GREEN + winner.getName()
                        + " wins the pot (everyone else folded).");
                Player wp = winner.getOnlinePlayer();
                if (wp != null) {
                    wp.playSound(wp.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
            } else {
                broadcast(ChatColor.RED + "No active players remain.");
            }
            endHand();
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
                endHand();
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

    // ---------- SHOWDOWN (NO ECONOMY) ----------

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
        broadcast(ChatColor.GRAY + "Hand over. Use /poker again to play another multiplayer hand.");
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

    private void announceTurn() {
        if (currentPlayerId == null) return;

        TablePlayer tp = players.get(currentPlayerId);
        if (tp == null) return;

        tp.updateActivity();

        Player p = tp.getOnlinePlayer();
        if (p == null || !p.isOnline()) return;

        broadcast(ChatColor.AQUA + "It is now " + tp.getName() + "'s turn.");

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
            if (tp.isFolded()) continue;

            Player p = tp.getOnlinePlayer();
            if (p == null || !p.isOnline()) continue;

            Card[] hand = tp.getHand();
            p.sendMessage(ChatColor.GRAY + "Your hand: "
                    + cardText(hand[0]) + ChatColor.GRAY + " and " + cardText(hand[1]));
        }
    }

    // ---------- AFK CHECKER ----------

    public void startAfkChecker() {
        Bukkit.getScheduler().runTaskTimer(LetsGambaPlugin.getInstance(), () -> {
            if (players.isEmpty()) return;

            long now = System.currentTimeMillis();

            for (TablePlayer tp : new ArrayList<>(players.values())) {
                Player p = tp.getOnlinePlayer();
                if (p == null || !p.isOnline()) continue;

                long inactiveMs = now - tp.getLastActionTime();

// 1:30 to 3:00 → send warning ONCE
                if (inactiveMs >= 90_000 && inactiveMs < 180_000) {
                    if (!tp.isAfkWarned()) {
                        p.sendMessage(ChatColor.YELLOW + "You have been inactive for 1 minute 30 seconds.");
                        p.sendMessage(ChatColor.YELLOW + "You will be removed from the poker table if inactive for 3 minutes.");
                        tp.setAfkWarned(true); // mark as warned so we don't spam
                    }
                }

// 3:00+ → kick every check (fine, they’re already gone after first)
                if (inactiveMs >= 180_000) {
                    p.sendMessage(ChatColor.RED + "You have been removed from the poker table for being AFK.");
                    leave(p);
                }
            }
        }, 20L, 200L); // start after 1 second, repeat every 10s
    }
}
