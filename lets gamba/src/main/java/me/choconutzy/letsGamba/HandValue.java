package me.choconutzy.letsGamba;

import java.util.Arrays;

public class HandValue implements Comparable<HandValue> {

    private final HandCategory category;
    // up to 5 ranks used for tie-breaking, from highest to lowest
    private final int[] ranks; // 2..14 (where 14 = Ace)

    public HandValue(HandCategory category, int... ranks) {
        this.category = category;
        this.ranks = ranks;
    }

    public HandCategory getCategory() {
        return category;
    }

    public int[] getRanks() {
        return ranks;
    }

    @Override
    public int compareTo(HandValue other) {
        // compare category first
        int catDiff = Integer.compare(this.category.ordinal(), other.category.ordinal());
        if (catDiff != 0) return catDiff;

        // same category -> compare ranks lexicographically
        int len = Math.min(this.ranks.length, other.ranks.length);
        for (int i = 0; i < len; i++) {
            int diff = Integer.compare(this.ranks[i], other.ranks[i]);
            if (diff != 0) return diff;
        }
        return Integer.compare(this.ranks.length, other.ranks.length);
    }

    public String getReadableName() {
        // Basic human-friendly name
        return switch (category) {
            case HIGH_CARD -> "High Card";
            case ONE_PAIR -> "One Pair";
            case TWO_PAIR -> "Two Pair";
            case THREE_OF_A_KIND -> "Three of a Kind";
            case STRAIGHT -> "Straight";
            case FLUSH -> "Flush";
            case FULL_HOUSE -> "Full House";
            case FOUR_OF_A_KIND -> "Four of a Kind";
            case STRAIGHT_FLUSH -> {
                // Ace-high straight flush = Royal Flush
                if (ranks.length > 0 && ranks[0] == 14) yield "Royal Flush";
                yield "Straight Flush";
            }
        };
    }

    @Override
    public String toString() {
        return "HandValue{" +
                "category=" + category +
                ", ranks=" + Arrays.toString(ranks) +
                '}';
    }
}
