package rummy.Meld;

import ch.aplu.jcardgame.Card;

import java.util.ArrayList;
import java.util.List;

// ---------- Composite ----------
public class CompositeMeld implements MeldComponent {

    private final List<MeldComponent> components;

    public CompositeMeld() {
        this.components = new ArrayList<>();
    }

    public void add(MeldComponent component) {
        components.add(component);
    }


    @Override
    public List<List<Card>> getMelds(List<Card> cards) {
        // We'll collect all possible melds from children
        List<List<Card>> allMelds = new ArrayList<>();

        for (MeldComponent component : components) {
            allMelds.addAll(component.getMelds(cards));
        }

        // Now, find combinations of melds that cover the most cards
        List<List<Card>> bestCombination = new ArrayList<>();
        findBestMeldCombination(cards, new ArrayList<>(), 0, bestCombination);

        return bestCombination;
    }

    // Recursive helper to find the combination of melds using the most cards
    private void findBestMeldCombination(List<Card> remainingCards, List<List<Card>> current, int start, List<List<Card>> best) {
        if (remainingCards.isEmpty()) {
            // All cards used, best possible
            best.clear();
            best.addAll(current);
            return;
        }

        boolean found = false;
        for (int i = start; i < components.size(); i++) {
            List<List<Card>> melds = components.get(i).getMelds(remainingCards);
            for (List<Card> meld : melds) {
                List<Card> newRemaining = new ArrayList<>(remainingCards);
                newRemaining.removeAll(meld);

                List<List<Card>> newCurrent = new ArrayList<>(current);
                newCurrent.add(meld);

                findBestMeldCombination(newRemaining, newCurrent, i, best);
                found = true;
            }
        }

        if (!found) {
            // No more melds found; current is the best we can do for remaining
            if (current.stream().mapToInt(List::size).sum() > best.stream().mapToInt(List::size).sum()) {
                best.clear();
                best.addAll(current);
            }
        }
    }
    public boolean isValid(List<Card> hand) {
        // A hand is valid if all cards are part of a meld (no deadwood)
        return getDeadwood(hand).isEmpty();
    }

    public List<Card> getDeadwood(List<Card> hand) {
        List<List<Card>> melds = getMelds(hand);
        List<Card> used = new ArrayList<>();
        for (List<Card> meld : melds) {
            used.addAll(meld);
        }
        List<Card> deadwood = new ArrayList<>(hand);
        deadwood.removeAll(used);
        return deadwood;
    }

}
