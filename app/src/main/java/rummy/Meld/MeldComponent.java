package rummy.Meld;

import ch.aplu.jcardgame.Card;

import java.util.ArrayList;
import java.util.List;

// ---------- Component ----------
public interface MeldComponent {
    // Return all valid melds this component can form from the given cards
    List<List<Card>> getMelds(List<Card> cards);

    // Return the remaining cards not used in any melds
    default List<Card> getDeadwood(List<Card> cards) {
        List<List<Card>> melds = getMelds(cards);
        if (melds.isEmpty()) return new ArrayList<>(cards);

        // Choose combination that uses the most cards (minimal deadwood)
        List<Card> bestRemaining = new ArrayList<>(cards);
        for (List<Card> meld : melds) {
            List<Card> remaining = new ArrayList<>(cards);
            remaining.removeAll(meld);
            if (remaining.size() < bestRemaining.size()) {
                bestRemaining = remaining;
            }
        }
        return bestRemaining;
    }

    default boolean isValid(List<Card> cards) {
        return getDeadwood(cards).isEmpty();
    }
}