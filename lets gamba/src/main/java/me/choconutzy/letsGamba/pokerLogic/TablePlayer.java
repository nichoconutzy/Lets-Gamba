package me.choconutzy.letsGamba.pokerLogic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.math.BigDecimal;

import java.util.UUID;

public class TablePlayer {

    private final UUID uuid;
    private final String name;
    private final Card[] hand = new Card[2];

    private boolean folded = false;
    private boolean actedThisStreet = false;

    private boolean afkWarned = false;

    private BigDecimal betThisRound = BigDecimal.ZERO;
    private boolean allIn = false;
    public BigDecimal getBetThisRound() {
        return betThisRound;
    }

    public void setBetThisRound(BigDecimal bet) {
        this.betThisRound = bet;
    }

    public void clearBetThisRound() {
        this.betThisRound = BigDecimal.ZERO;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public void setAllIn(boolean allIn) {
        this.allIn = allIn;
    }

    public boolean isAfkWarned() {
        return afkWarned;
    }

    public void setAfkWarned(boolean afkWarned) {
        this.afkWarned = afkWarned;
    }

    private long lastActionTime;

    public TablePlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.lastActionTime = System.currentTimeMillis();
    }


    public void updateActivity() {
        lastActionTime = System.currentTimeMillis();
        afkWarned = false; // any action = not AFK / not warned
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Card[] getHand() {
        return hand;
    }

    public boolean isFolded() {
        return folded;
    }

    public void setFolded(boolean folded) {
        this.folded = folded;
    }

    public boolean hasActedThisStreet() {
        return actedThisStreet;
    }

    public void setActedThisStreet(boolean actedThisStreet) {
        this.actedThisStreet = actedThisStreet;
    }

    public void resetForNewHand() {
        this.folded = false;
        this.actedThisStreet = false;
        betThisRound = BigDecimal.ZERO;
        allIn = false;

    }

    public void resetForNewStreet() {
        this.actedThisStreet = false;
    }

    public Player getOnlinePlayer() {
        return Bukkit.getPlayer(uuid);
    }
}
