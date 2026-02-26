package rummy.Meld;

import ch.aplu.jcardgame.Card;
import rummy.Rank;
import rummy.Suit;

import java.util.*;
import java.util.stream.Collectors;

// ---------- Leaf: SetMeld ----------
public class SetMeld implements MeldComponent {
    @Override
    public List<List<Card>> getMelds(List<Card> cards) {
        List<List<Card>> sets = new ArrayList<>();

        Map<Rank, List<Card>> rankGroups = cards.stream()
                .collect(Collectors.groupingBy(c -> (Rank) c.getRank()));

        for (List<Card> sameRankCards : rankGroups.values()) {
            Set<Suit> suits = new HashSet<>();
            List<Card> validSet = new ArrayList<>();
            for (Card c : sameRankCards) {
                Suit suit = (Suit) c.getSuit();
                if (!suits.contains(suit)) {
                    suits.add(suit);
                    validSet.add(c);
                }
            }
            if (validSet.size() >= 3 && validSet.size() <= 4) {
                sets.add(validSet);
            }
        }

        return sets;
    }
}