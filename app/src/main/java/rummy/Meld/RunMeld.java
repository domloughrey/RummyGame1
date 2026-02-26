package rummy.Meld;

import ch.aplu.jcardgame.Card;
import rummy.Rank;
import rummy.Suit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ---------- Leaf: RunMeld ----------
public class RunMeld implements MeldComponent {
    @Override
    public List<List<Card>> getMelds(List<Card> cards) {
        List<List<Card>> runs = new ArrayList<>();

        Map<Suit, List<Card>> suitGroups = cards.stream()
                .collect(Collectors.groupingBy(c -> (Suit) c.getSuit()));

        for (List<Card> suitCards : suitGroups.values()) {
            suitCards.sort(Comparator.comparingInt(c -> ((Rank) c.getRank()).getShortHandValue()));

            for (int start = 0; start < suitCards.size(); start++) {
                List<Card> run = new ArrayList<>();
                run.add(suitCards.get(start));
                for (int i = start + 1; i < suitCards.size(); i++) {
                    int prevVal = ((Rank) suitCards.get(i - 1).getRank()).getShortHandValue();
                    int currVal = ((Rank) suitCards.get(i).getRank()).getShortHandValue();
                    if (currVal == prevVal + 1) run.add(suitCards.get(i));
                    else break;
                }
                if (run.size() >= 3) runs.add(run);
            }
        }

        return runs;
    }
}
