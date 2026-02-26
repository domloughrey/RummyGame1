package rummy.ai;

import ch.aplu.jcardgame.Card;
import ch.aplu.jcardgame.Hand;
import rummy.MeldAnalyser;
import rummy.Rank;
import rummy.Suit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Feature 3: Smart Computer player decisions, independent of scoring and mode rules.
 */
public class SmartComputer {

    public interface DeclarationPolicy {
        boolean isKnockAllowed();     // false in Classic, true in Gin
        int knockThreshold();         // 7 in Gin, ignored in Classic
    }

    public interface Actions {
        void takeFromDiscard();       // pick up top card from discard pile
        Card drawFromStock();         // pick up card from stockpile
        void discard(Card c);         // remove from hand and push to discard
        void declareAll();            // "Rummy" or "Gin"
        void declareKnock();          // Gin only
        void endTurn();
        Card peekDiscardTop();        // null if discard pile empty
        Hand getMyHand();             // computer player's hand
    }

    private final MeldAnalyser analyser;
    private final Random rnd; // for ultimate tie only

    public SmartComputer(MeldAnalyser analyser, long seed) {
        this.analyser = analyser;
        this.rnd = new Random(seed);
    }

    /** Entry: call once on the computer's turn. */
    public void playTurn(Actions game, DeclarationPolicy policy) {
        Hand hand = game.getMyHand();
        Card top = game.peekDiscardTop();

        // Views of current hand (suit counts / min gap) for quick checks
        Views views = Views.of(hand);

        // Step 1: consider the top of discard pile using the four criteria (any true -> keep)
        if (top != null && anyCriterionHelpful(top, hand, views)) {
            game.takeFromDiscard();
            afterKeepingCard(game, policy);  // Step 3 + 4
            return;
        }

        // Step 2: draw from stock
        Card drawn = game.drawFromStock();
        if (drawn == null) { // stock exhausted safety
            game.endTurn();
            return;
        }

        if (anyCriterionHelpful(drawn, hand, views)) {
            // keep drawn (it's already in hand because drawFromStock should add to hand)
            afterKeepingCard(game, policy);
        } else {
            // discard drawn immediately
            game.discard(drawn);
            maybeDeclareOrEnd(game, policy); // Step 4
        }
    }

    // --- Step 3: choose discard and drop it, then Step 4: maybe declare ---

    private void afterKeepingCard(Actions game, DeclarationPolicy policy) {
        Hand hand = game.getMyHand();
        // Recompute best melds/deadwood on the updated hand
        Best best = bestCover(hand);

        Card toDiscard = chooseDiscard(hand, best);
        game.discard(toDiscard);

        maybeDeclareOrEnd(game, policy);
    }

    private void maybeDeclareOrEnd(Actions game, DeclarationPolicy policy) {
        Hand hand = game.getMyHand();
        Best best = bestCover(hand);

        // "Rummy/Gin": all cards are in melds (no deadwood)
        if (best.deadwood.isEmpty()) {
            game.declareAll();
            return;
        }
        // Gin Knock rule
        if (policy.isKnockAllowed() && best.deadwoodPoints <= policy.knockThreshold()) {
            game.declareKnock();
            return;
        }
        game.endTurn();
    }

    // --- Criteria: return true if drawn card is "helpful" per any criterion ---

    private boolean anyCriterionHelpful(Card drawn, Hand current, Views viewsBefore) {
        // C1: Immediate meld (drawn is used in a meld now)
        if (immediateMeld(drawn, current)) return true;

        // C2: Decrease minimum rank gap of same suit OR current had only one of that suit
        if (minGapImprovesOrSingleSuit(drawn, current, viewsBefore)) return true;

        // C3: Increase the maximum cards of any suit
        if (increasesMaxSuitCount(drawn, current, viewsBefore)) return true;

        // C4: Increase same-rank count in DEADWOOD after recomputing best cover
        if (increasesDeadwoodSameRank(drawn, current)) return true;

        return false;
    }

    // Criterion 1: drawn participates in a meld immediately
    private boolean immediateMeld(Card drawn, Hand current) {
        Best withDrawn = bestCover(current, drawn);
        // helpful if drawn is not in deadwood (i.e., used in some meld)
        return !withDrawn.deadwood.contains(drawn);
    }

    // Criterion 2: min gap improves for same suit, or there was only one card in that suit
    private boolean minGapImprovesOrSingleSuit(Card c, Hand current, Views before) {
        Suit s = (Suit) c.getSuit();
        List<Card> withList = new ArrayList<>(current.getCardList());
        List<Card> withoutList = new ArrayList<>(withList);
        withoutList.remove(c);

        Views with = Views.ofList(withList);
        Views without = Views.ofList(withoutList);

        if (without.suitCount(s) <= 1) return true;
        int gapWith = with.minGap(s);
        int gapWithout = without.minGap(s);
        return gapWith < gapWithout;
    }

    // Criterion 3: increases the maximum suit count
    private boolean increasesMaxSuitCount(Card drawn, Hand current, Views before) {
        int maxBefore = before.maxSuitCount();
        Views after = Views.ofPlusOne(current, drawn);
        int countDrawnSuit = after.suitCount((Suit) drawn.getSuit());
        return countDrawnSuit > maxBefore;
    }

    // Criterion 4: increases same-rank count in DEADWOOD after best cover
    private boolean increasesDeadwoodSameRank(Card drawn, Hand current) {
        Best withDrawn = bestCover(current, drawn);
        int r = ((Rank) drawn.getRank()).getShortHandValue(); // A=1 ... K=13
        int cnt = 0;
        for (Card c : withDrawn.deadwood) {
            if (((Rank) c.getRank()).getShortHandValue() == r) cnt++;
        }
        return cnt >= 2;
    }

    // --- Discard heuristic from spec  ---

    private Card chooseDiscard(Hand current, Best best) {
        List<Card> deadwood = best.deadwood;

        if (deadwood.isEmpty()) {
            for (Card c : new ArrayList<>(current.getCardList())) {
                if (keepsAllMeldsAfterRemoving(current, c)) {
                    return c; // ideal: after discard, deadwood still empty → we will declare
                }
            }
            // Very rare fallback: discard the card that yields the smallest deadwood points afterward
            return deadwoodMinDamage(current);
        }

        Views views = Views.of(current);

        // 1) Fewest criteria satisfied (treat each deadwood card as if it were "the drawn card")
        record Score(Card c, int satisfied) {}
        List<Score> scores = new ArrayList<>();
        int minSat = Integer.MAX_VALUE;
        for (Card c : deadwood) {
            int sat = countCriteriaSatisfiedForDiscard(c, current, views);
            scores.add(new Score(c, sat));
            if (sat < minSat) minSat = sat;
            System.out.println("Current sat for " + c.getRank() + " of " + c.getSuit() + " = " + sat);
        }

        List<Card> pool = new ArrayList<>();
        for (Score s : scores) {
            if (s.satisfied() == minSat) pool.add(s.c());
        }

        // 2) Least frequent suit (within deadwood)
        Map<Suit, Integer> freq = new EnumMap<>(Suit.class);
        for (Card c : deadwood) {
            Suit s = (Suit) c.getSuit();
            freq.merge(s, 1, Integer::sum);
        }

        pool.sort((a, b) -> {
            int fa = freq.getOrDefault((Suit) a.getSuit(), 0);
            int fb = freq.getOrDefault((Suit) b.getSuit(), 0);
            if (fa != fb) return Integer.compare(fa, fb);
            // 3) Highest value (K/Q/J=10, A=1, others face)
            int va = cardValue(a), vb = cardValue(b);
            return Integer.compare(vb, va);
        });

        // If still tied (same suit and same value), pick deterministically
        int topFreq = freq.getOrDefault((Suit) pool.get(0).getSuit(), 0);
        int topVal = cardValue(pool.get(0));
        List<Card> tops = new ArrayList<>();
        for (Card c : pool) {
            if (freq.getOrDefault((Suit) c.getSuit(), 0) == topFreq && cardValue(c) == topVal) {
                tops.add(c);
            } else break;
        }
        return tops.get(rnd.nextInt(tops.size()));
    }

    // Keep 13 all-melded after removing "remove"?
    private boolean keepsAllMeldsAfterRemoving(Hand current, Card remove) {
        List<Card> cards = new ArrayList<>(current.getCardList());
        cards.remove(remove);
        return analyser.getDeadwood(cards).isEmpty();
    }

    // Rare fallback when perfect-14 not possible: minimize deadwood points after discard
    private Card deadwoodMinDamage(Hand current) {
        List<Card> hand = new ArrayList<>(current.getCardList());
        int bestPts = Integer.MAX_VALUE;
        List<Card> best = new ArrayList<>();
        for (Card c : hand) {
            List<Card> after = new ArrayList<>(hand);
            after.remove(c);
            int pts = analyser.getDeadwoodPoints(analyser.getDeadwood(after));
            if (pts < bestPts) { bestPts = pts; best.clear(); best.add(c); }
            else if (pts == bestPts) best.add(c);
        }
        // tie-break: highest card points
        best.sort((a, b) -> Integer.compare(cardValue(b), cardValue(a)));
        return best.get(0);
    }

    // For Step 3 (discard ranking), we score using E2/E3/E4 only (no E1).
    private int countCriteriaSatisfiedForDiscard(Card asDeadwood, Hand current, Views before) {
        int c = 0;
        if (minGapImprovesOrSingleSuit(asDeadwood, current, before)) c++;      // E2
        if (increasesMaxSuitCount(asDeadwood, current, before)) c++;           // E3
        if (increasesDeadwoodSameRank(asDeadwood, current)) c++;               // E4
        return c;
    }

    private int cardValue(Card c) {
        int r = ((Rank) c.getRank()).getShortHandValue(); // 1..13
        if (r == 1) return 1;
        if (r >= 11) return 10;
        return r;
    }

    // --- Best-meld cover using your MeldAnalyser (INFO EXPERT) ---

    private Best bestCover(Hand hand) {
        return bestCover(hand, null);
    }

    private Best bestCover(Hand hand, Card plusOne) {
        List<Card> cards = new ArrayList<>(hand.getCardList());
        if (plusOne != null) cards.add(plusOne);

        List<List<Card>> melds = analyser.getBestMelds(cards);       // each meld is a list of cards
        List<Card> deadwood = analyser.getDeadwood(cards);           // cards not used in melds
        int points = analyser.getDeadwoodPoints(deadwood);           // already in your class

        return new Best(melds, deadwood, points);
    }

    // --- small data holders ---

    private record Best(List<List<Card>> melds, List<Card> deadwood, int deadwoodPoints) {}

    /** Lightweight views of a hand for suit counts & min gap. */
    private static final class Views {
        private final Map<Suit, List<Integer>> ranksBySuit = new EnumMap<>(Suit.class);
        private final Map<Suit, Integer> suitCounts = new EnumMap<>(Suit.class);

        // Build from a Hand
        static Views of(Hand hand) {
            return new Views(new ArrayList<>(hand.getCardList()));
        }

        // Build from Hand + one extra card (no Hand/Deck construction)
        static Views ofPlusOne(Hand hand, Card plusOne) {
            ArrayList<Card> cards = new ArrayList<>(hand.getCardList());
            cards.add(plusOne);
            return new Views(cards);
        }

        static Views ofList(List<Card> cards) { return new Views(cards); }

        // Core constructor: work from a plain list of cards
        private Views(List<Card> cards) {
            for (Suit s : Suit.values()) {
                ranksBySuit.put(s, new ArrayList<>());
                suitCounts.put(s, 0);
            }
            for (Card c : cards) {
                Suit s = (Suit) c.getSuit();
                int r = ((Rank) c.getRank()).getShortHandValue();
                ranksBySuit.get(s).add(r);
                suitCounts.put(s, suitCounts.get(s) + 1);
            }
            for (var e : ranksBySuit.entrySet()) e.getValue().sort(Integer::compare);
        }

        int suitCount(Suit s) { return suitCounts.getOrDefault(s, 0); }
        int maxSuitCount() { return suitCounts.values().stream().max(Integer::compareTo).orElse(0); }

        // min gap = min(next - prev - 1) over consecutive ranks in a suit; INF if <2 cards
        int minGap(Suit s) {
            List<Integer> rs = ranksBySuit.get(s);
            if (rs == null || rs.size() < 2) return Integer.MAX_VALUE;
            int best = Integer.MAX_VALUE;
            for (int i = 1; i < rs.size(); i++) best = Math.min(best, rs.get(i) - rs.get(i - 1) - 1);
            return best;
        }
    }
}
