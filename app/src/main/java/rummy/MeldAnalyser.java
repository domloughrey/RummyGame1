package rummy;

import ch.aplu.jcardgame.Card;
import rummy.Meld.CompositeMeld;
import rummy.Meld.MeldComponent;
import rummy.Meld.RunMeld;
import rummy.Meld.SetMeld;

import java.util.List;

public class MeldAnalyser {

    private final MeldComponent combo;

    public MeldAnalyser() {
        MeldComponent composite = new CompositeMeld();
        ((CompositeMeld) composite).add(new RunMeld());
        ((CompositeMeld) composite).add(new SetMeld());
        this.combo = composite;  // assign to field

    }

    /** Check if all cards form valid melds */
    public boolean isValidHand(List<Card> hand) {
        return combo.isValid(hand);
    }

    /** Get best melds for this hand */
    public List<List<Card>> getBestMelds(List<Card> hand) {
        return combo.getMelds(hand);
    }

    /** Get deadwood cards */
    public List<Card> getDeadwood(List<Card> hand) {
        return combo.getDeadwood(hand);
    }

    /** Calculate total points for a list of deadwood cards */
    public int getDeadwoodPoints(List<Card> deadwood) {
        int points = 0;
        for (Card card : deadwood) {
            Rank rank = (Rank) card.getRank();
            int val = rank.getShortHandValue();
            if (val > 10) val = 10; // cap at 10 for J, Q, K
            points += val;
        }
        return points;
    }
}
