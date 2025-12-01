package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.Commands.gambaCommand;
import me.choconutzy.letsGamba.LetsGambaPlugin;
import me.choconutzy.letsGamba.handLogic.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PokerGame {

    private final Player player;
    private final Deck deck;

    private final Card[] playerHand = new Card[2];
    private final Card[] dealerHand = new Card[2];
    private final List<Card> board = new ArrayList<>();

    private GameStage stage = GameStage.PRE_FLOP;

    private void sendSeparator() {
        player.sendMessage(ChatColor.GRAY + "----------------------------------------");
    }

    public PokerGame(Player player) {
        this.player = player;
        this.deck = new Deck();

        // deal hole cards
        playerHand[0] = deck.draw();
        playerHand[1] = deck.draw();

        dealerHand[0] = deck.draw();
        dealerHand[1] = deck.draw();

        player.sendMessage(ChatColor.AQUA + "New game started!");
        player.sendMessage(ChatColor.AQUA + "Your hand: "
                + ChatColor.YELLOW + cardText(playerHand[0]) + ChatColor.AQUA + " and "
                + ChatColor.YELLOW + cardText(playerHand[1]));
        player.sendMessage(ChatColor.GRAY + "(Dealer cards are face down.)"); //buh
    }

    public GameStage getStage() {
        return stage;
    }

    private String cardText(Card c) {
        return c.getRank() + " of " + c.getSuit();
    }

    public void handleAction(PokerAction action) {
        if (stage == GameStage.FINISHED) {
            player.sendMessage(ChatColor.RED + "This game has ended. Use /poker again to start a new one.");
            return;
        }

        switch (action) {
            case FOLD:
                handleFold();
                break;
            case CHECK_OR_CALL:
            case RAISE:
                handleContinue(action);
                break;
        }
    }

    private void handleFold() {
        player.sendMessage(ChatColor.RED + "You folded. Dealer wins this round.");
        stage = GameStage.FINISHED;
    }

    private void handleContinue(PokerAction action) {
        switch (stage) {
            case PRE_FLOP -> {
                // deal flop 3 card
                board.add(deck.draw());
                board.add(deck.draw());
                board.add(deck.draw());
                stage = GameStage.FLOP;

                player.sendMessage(ChatColor.GREEN + "Flop:");
                sendBoard();

                // show clickable buttons again
                sendChatButtons(player);
                sendSeparator();
            }

            case FLOP -> {
                // deal turn 1 card
                board.add(deck.draw());
                stage = GameStage.TURN;
                player.sendMessage(ChatColor.GREEN + "Turn:");
                sendBoard();

                // show clickable buttons again
                sendChatButtons(player);
                sendSeparator();
            }
            case TURN -> {
                // deal river 1 card
                board.add(deck.draw());
                stage = GameStage.RIVER;
                player.sendMessage(ChatColor.GREEN + "River:");
                sendBoard();
                sendChatButtons(player);
                sendSeparator();
            }

            case RIVER -> {
                // showdown, no more buttons after this
                stage = GameStage.SHOWDOWN;
                showDown();
                stage = GameStage.FINISHED;
                
            }

            default -> {
            }
        }
    }

    private void sendBoard() {
        StringBuilder sb = new StringBuilder(ChatColor.YELLOW + "Board: ");
        for (Card c : board) {
            sb.append(ChatColor.GOLD)
                    .append("[")
                    .append(cardText(c))
                    .append("] ");
        }
        player.sendMessage(sb.toString());
    }

    private void showDown() {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Showdown time!");

        // Build 7-card hands: 2 hole + 5 board
        List<Card> playerCards = new ArrayList<>();
        Collections.addAll(playerCards, playerHand[0], playerHand[1]);
        playerCards.addAll(board);

        List<Card> dealerCards = new ArrayList<>();
        Collections.addAll(dealerCards, dealerHand[0], dealerHand[1]);
        dealerCards.addAll(board);

        HandValue playerValue = HandEvaluator.evaluateSeven(playerCards);
        HandValue dealerValue = HandEvaluator.evaluateSeven(dealerCards);

        // Show cards & board
        player.sendMessage(ChatColor.AQUA + "Your hand: "
                + ChatColor.YELLOW + cardText(playerHand[0]) + ChatColor.AQUA + " and "
                + ChatColor.YELLOW + cardText(playerHand[1]));

        player.sendMessage(ChatColor.AQUA + "Dealer hand: " //buh
                + ChatColor.YELLOW + cardText(dealerHand[0]) + ChatColor.AQUA + " and "
                + ChatColor.YELLOW + cardText(dealerHand[1]));

        sendBoard();

        // Show categories
        player.sendMessage(ChatColor.GREEN + "Your best hand: " + ChatColor.GOLD + playerValue.getReadableName());
        player.sendMessage(ChatColor.RED + "Dealer best hand: " + ChatColor.GOLD + dealerValue.getReadableName()); //buh

        int cmp = playerValue.compareTo(dealerValue);

        // Dealer qualifies with pair or better
        boolean dealerQualifies = dealerValue.getCategory().ordinal() >= HandCategory.ONE_PAIR.ordinal();

        if (cmp > 0) {
            if (dealerQualifies) {
                player.sendMessage(ChatColor.GREEN + "You WIN and dealer qualifies!"); //buh
            } else {
                player.sendMessage(ChatColor.GREEN + "You WIN but dealer does NOT qualify."); //buh
            }
            player.sendMessage(ChatColor.GRAY + "(Bet payouts not implemented yet – this is result only.)");
        } else if (cmp < 0) {
            player.sendMessage(ChatColor.RED + "Dealer wins this round."); //buh
        } else {
            player.sendMessage(ChatColor.YELLOW + "It's a PUSH (tie).");
        }
    }

    private void sendChatButtons(Player player) {
        gambaCommand cmd = (gambaCommand) LetsGambaPlugin.getInstance()
                .getCommand("poker")
                .getExecutor();

        if (cmd != null) {
            cmd.sendActionMenu(player);  // sendActionMenu must be public
        }
    }
}
