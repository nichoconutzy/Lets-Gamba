package me.choconutzy.letsGamba;

import java.util.*;

public class HandEvaluator {

    // Evaluates best 5-card hand from 7 cards
    public static HandValue evaluateSeven(List<Card> cards) {
        if (cards.size() < 5) {
            throw new IllegalArgumentException("Need at least 5 cards to evaluate");
        }

        // Map ranks: TWO..ACE -> 2..14, index 0..12
        int[] rankCounts = new int[13];
        int[] suitCounts = new int[4];

        for (Card c : cards) {
            int rIndex = rankIndex(c.getRank());
            int sIndex = suitIndex(c.getSuit());
            rankCounts[rIndex]++;
            suitCounts[sIndex]++;
        }

        // Collect ranks sorted high->low, including duplicates
        List<Integer> allRanks = new ArrayList<>();
        for (int r = 12; r >= 0; r--) {
            for (int i = 0; i < rankCounts[r]; i++) {
                allRanks.add(r + 2);
            }
        }

        // Unique ranks high->low for straights
        List<Integer> uniqueRanks = new ArrayList<>();
        boolean[] seen = new boolean[13];
        for (int r = 12; r >= 0; r--) {
            if (rankCounts[r] > 0 && !seen[r]) {
                seen[r] = true;
                uniqueRanks.add(r + 2);
            }
        }
        // Check for Flush / Straight Flush / Royal Flush
        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            if (suitCounts[s] >= 5) {
                flushSuit = s;
                break;
            }
        }

        List<Integer> flushRanks = null;
        if (flushSuit != -1) {
            flushRanks = new ArrayList<>();
            for (Card c : cards) {
                if (suitIndex(c.getSuit()) == flushSuit) {
                    flushRanks.add(rankValue(c.getRank()));
                }
            }
            flushRanks.sort(Collections.reverseOrder());
        }

        // Straight Flush And Royal Flush
        if (flushRanks != null) {
            HandValue sf = detectStraightFromRanks(flushRanks);
            if (sf != null) {
                if (sf.getRanks()[0] == 14) {
                    return new HandValue(HandCategory.ROYAL_FLUSH, sf.getRanks());
                }
                return new HandValue(HandCategory.STRAIGHT_FLUSH, sf.getRanks());
            }
        }

        // Four of a kind?
        int fourRank = -1;
        for (int r = 12; r >= 0; r--) {
            if (rankCounts[r] == 4) {
                fourRank = r + 2;
                break;
            }
        }
        if (fourRank != -1) {
            int kicker = findHighestExcluding(rankCounts, fourRank);
            return new HandValue(HandCategory.FOUR_OF_A_KIND, fourRank, kicker);
        }

        // Full house?
        int threeRank1 = -1;
        int threeRank2 = -1;
        for (int r = 12; r >= 0; r--) {
            if (rankCounts[r] >= 3) {
                if (threeRank1 == -1) threeRank1 = r + 2;
                else if (threeRank2 == -1) threeRank2 = r + 2;
            }
        }
        int pairRank = -1;
        if (threeRank1 != -1) {
            for (int r = 12; r >= 0; r--) {
                int val = r + 2;
                if (val == threeRank1) continue;
                if (rankCounts[r] >= 2) {
                    pairRank = val;
                    break;
                }
            }
        }

        if (threeRank1 != -1 && (pairRank != -1 || threeRank2 != -1)) {
            int usedThree = threeRank1;
            int usedPair = (pairRank != -1) ? pairRank : threeRank2;
            return new HandValue(HandCategory.FULL_HOUSE, usedThree, usedPair);
        }

        // Flush?
        if (flushRanks != null) {
            int[] best = flushRanks.stream().limit(5).mapToInt(Integer::intValue).toArray();
            return new HandValue(HandCategory.FLUSH, best);
        }

        // Straight?
        HandValue straight = detectStraightFromRanks(uniqueRanks);
        if (straight != null) {
            return new HandValue(HandCategory.STRAIGHT, straight.getRanks());
        }

        // Three of a kind?
        if (threeRank1 != -1) {
            List<Integer> kickers = new ArrayList<>();
            for (int r = 12; r >= 0; r--) {
                int val = r + 2;
                if (val == threeRank1) continue;
                if (rankCounts[r] > 0) {
                    kickers.add(val);
                    if (kickers.size() == 2) break;
                }
            }
            return new HandValue(HandCategory.THREE_OF_A_KIND,
                    threeRank1, kickers.get(0), kickers.get(1));
        }

        // Two pair?
        List<Integer> pairRanks = new ArrayList<>();
        for (int r = 12; r >= 0; r--) {
            if (rankCounts[r] >= 2) {
                pairRanks.add(r + 2);
            }
        }
        if (pairRanks.size() >= 2) {
            int highPair = pairRanks.get(0);
            int lowPair = pairRanks.get(1);
            int kicker = findHighestExcluding(rankCounts, highPair, lowPair);
            return new HandValue(HandCategory.TWO_PAIR, highPair, lowPair, kicker);
        }

        // One pair?
        if (pairRanks.size() == 1) {
            int pair = pairRanks.get(0);
            List<Integer> kickers = new ArrayList<>();
            for (int r = 12; r >= 0; r--) {
                int val = r + 2;
                if (val == pair) continue;
                if (rankCounts[r] > 0) {
                    kickers.add(val);
                    if (kickers.size() == 3) break;
                }
            }
            return new HandValue(HandCategory.ONE_PAIR,
                    pair, kickers.get(0), kickers.get(1), kickers.get(2));
        }

        // High card
        int[] top5 = allRanks.stream().limit(5).mapToInt(Integer::intValue).toArray();
        return new HandValue(HandCategory.HIGH_CARD, top5);
    }

    private static int rankIndex(Card.Rank r) {
        return r.ordinal(); // TWO=0 .. ACE=12
    }

    private static int rankValue(Card.Rank r) {
        return r.ordinal() + 2; // 2..14
    }

    private static int suitIndex(Card.Suit s) {
        return s.ordinal(); // CLUBS=0..SPADES=3
    }

    // Detects highest straight from a list of ranks high->low (may contain duplicates)
    private static HandValue detectStraightFromRanks(List<Integer> ranks) {
        if (ranks.size() < 5) return null;

        // Remove duplicates while preserving order
        List<Integer> unique = new ArrayList<>();
        Integer prev = null;
        for (int r : ranks) {
            if (prev == null || r != prev) {
                unique.add(r);
                prev = r;
            }
        }

        int consecutive = 1;
        int startHigh = unique.get(0);

        for (int i = 1; i < unique.size(); i++) {
            int curr = unique.get(i);
            if (curr == unique.get(i - 1) - 1) {
                consecutive++;
                if (consecutive >= 5) {
                    startHigh = unique.get(i - 4);
                    // we keep scanning to see if there is an even higher straight
                }
            } else {
                consecutive = 1;
            }
        }

        // Wheel (A-2-3-4-5)
        boolean hasAce = unique.contains(14);
        if (consecutive < 5 && hasAce &&
                unique.contains(5) && unique.contains(4) &&
                unique.contains(3) && unique.contains(2)) {
            return new HandValue(HandCategory.STRAIGHT, 5);
        }

        if (consecutive >= 5) {
            return new HandValue(HandCategory.STRAIGHT, startHigh);
        }
        return null;
    }

    private static int findHighestExcluding(int[] rankCounts, int... excludeRanks) {
        Set<Integer> exclude = new HashSet<>();
        for (int v : excludeRanks) exclude.add(v);

        for (int r = 12; r >= 0; r--) {
            int val = r + 2;
            if (exclude.contains(val)) continue;
            if (rankCounts[r] > 0) {
                return val;
            }
        }
        return 0;
    }
}
