package rummy;

import ch.aplu.jcardgame.*;
import ch.aplu.jgamegrid.*;

import java.util.Comparator;
import java.util.List;

public class RummyTurnController {

    private final CardGame ui;  // new

    private final MeldAnalyser meldAnalyser; // validation
    private final GameMode gameMode;         // Strategy (GinRummy/ClassicRummy)

    // Game state refs owned by Rummy, passed in
    private final Hand pack;
    private final Hand discard;
    private final Hand[] hands;
    private Hand chosenPile = null;

    // UI: buttons
    private final GGButton endTurnButton = new GGButton("sprites/end.gif", false);
    private final GGButton rummyButton  = new GGButton("sprites/rummy.gif", false);
    private final GGButton ginButton    = new GGButton("sprites/gin.gif", false);
    private final GGButton knockButton  = new GGButton("sprites/knock.gif", false);
    private final Location endTurnLocation = new Location(80, 610);
    private final Location rummyLocation = new Location(80, 650);
    private final Location ginLocation = new Location(80, 650);
    private final Location knockLocation = new Location(80, 690);

    // Interaction flags (private to controller)
    private boolean isEndingTurn = false;
    private boolean declaredRummy = false;
    private boolean declaredGin = false;
    private boolean declaredKnock = false;

    // Tunables
    private final int delayTimeMs;
    private final int thinkingTimeMs;

    RummyTurnController(
            CardGame ui, MeldAnalyser meldAnalyser, GameMode gameMode,
            Hand pack, Hand discard, Hand[] hands,
            int delayTimeMs,
            int thinkingTimeMs
    ) {
        this.ui = ui;
        this.meldAnalyser = meldAnalyser;
        this.gameMode = gameMode;
        this.pack = pack;
        this.discard = discard;
        this.hands = hands;
        this.delayTimeMs = delayTimeMs;
        this.thinkingTimeMs = thinkingTimeMs;

        setupButtons();
    }

    void resetDeclarations() {
        isEndingTurn = false;
        declaredRummy = false;
        declaredGin = false;
        declaredKnock = false;
    }

    // --------- Public Template Method for the HUMAN turn ----------
    // Returns what the human declared (or NONE), and sets selected/drawn inside the passed holders
    PlayerAction playHumanTurn(StatusIO io, CardHolder drawn, CardHolder discarded) {
        resetDeclarations();

        // 1) draw
        if (!discard.isEmpty()) {
            io.status("Your turn: double-click stockpile or discard to draw.");
            waitForPileChoice(pack, discard);
        } else {
            io.status("Your turn (first move): double-click stockpile to draw.");
            waitForPileChoice(pack, null);
        }
        drawn.card = drawInto(hands[io.HUMAN()]); // inserts + sorts + draws

        // 2) discard
        io.status("Double-click a card in hand to discard.");
        Card toDiscard = waitForHandSelection(hands[io.HUMAN()]);
        discardCard(toDiscard, hands[io.HUMAN()]);
        discarded.card = toDiscard;

        // 3) choose: end turn vs declarations
        enableChoiceButtonsByMode(true);
        io.status("Choose: End Turn or declare Rummy/Gin/Knock.");
        PlayerAction choice = waitForChoice();
        enableChoiceButtonsByMode(false);

        // Validate melded claims here for instant feedback.
        if (choice == PlayerAction.RUMMY || choice == PlayerAction.GIN) {
            List<Card> human = hands[io.HUMAN()].getCardList();
            if (!meldAnalyser.isValidHand(human)) {
                io.status("Invalid " + (choice== PlayerAction.GIN ? "Gin" : "Rummy") + " — hand not fully melded.");
                // fall back to end turn
                choice = PlayerAction.NONE;
            }
        }

        // If no declaration, we still need to wait for End Turn click (UX polish).
        if (choice == PlayerAction.NONE) {
            waitForEndTurn();
        }
        return choice;
    }

    private void enableChoiceButtonsByMode(boolean enable) {
        endTurnButton.setMouseTouchEnabled(enable);
        boolean isGin = (gameMode instanceof GinRummy);
        rummyButton.setMouseTouchEnabled(enable && !isGin);
        ginButton.setMouseTouchEnabled(enable && isGin);
        knockButton.setMouseTouchEnabled(enable && isGin);
    }

    private void waitLoopUntil(BooleanSupplier cond) {
        while (!cond.getAsBoolean()) GameGrid.delay(delayTimeMs);
    }

    // Wait for choice (declarations or end turn)
    private PlayerAction waitForChoice() {
        isEndingTurn = false;
        waitLoopUntil(() -> isEndingTurn || declaredRummy || declaredGin || declaredKnock);
        if (declaredRummy) return PlayerAction.RUMMY;
        if (declaredGin)   return PlayerAction.GIN;
        if (declaredKnock) return PlayerAction.KNOCK;
        return PlayerAction.NONE;
    }

    private void waitForEndTurn() {
        // Be tolerant if a declaration happens now—just stop waiting.
        isEndingTurn = false;
        endTurnButton.setMouseTouchEnabled(true);
        waitLoopUntil(() -> isEndingTurn || declaredRummy || declaredGin || declaredKnock);
        endTurnButton.setMouseTouchEnabled(false);
    }

    private void waitForPileChoice(Hand pile1, Hand pile2) {
        chosenPile = null;

        CardListener l1 = new CardAdapter() {
            public void leftDoubleClicked(Card c) { chosenPile = pile1; }
        };
        CardListener l2 = new CardAdapter() {
            public void leftDoubleClicked(Card c) { chosenPile = pile2; }
        };

        if (pile1 != null) {
            pile1.addCardListener(l1);
            pile1.setTouchEnabled(true);
        }
        if (pile2 != null) {
            pile2.addCardListener(l2);
            pile2.setTouchEnabled(true);
        }

        // wait until a pile is chosen or a declaration interrupts
        waitLoopUntil(() -> chosenPile != null || declaredRummy || declaredGin || declaredKnock);

        // tear down listeners & touches
        if (pile1 != null) { pile1.setTouchEnabled(false);  }
        if (pile2 != null) { pile2.setTouchEnabled(false);  }

    }

    private Card waitForHandSelection(Hand hand) {
        hand.setTouchEnabled(true);
        final Card[] chosen = {null};
        hand.addCardListener(new CardAdapter() {
            public void leftDoubleClicked(Card card) { chosen[0] = card; }
        });
        waitLoopUntil(() -> chosen[0] != null);
        hand.setTouchEnabled(false);
        return chosen[0];
    }

    private Card drawInto(Hand hand) {
        GameGrid.delay(thinkingTimeMs);

        Hand pile = (chosenPile != null) ? chosenPile : (!discard.isEmpty() ? discard : pack);

        Card card = dealTop(pile);
        pile.remove(card, false);
        pile.draw();

        hand.insert(card, false);
        sortHand(hand);
        hand.draw();

        chosenPile = null; // reset for next time
        return card;
    }

    private void discardCard(Card card, Hand hand) {
        card.removeFromHand(false);
        discard.insert(card, false);
        discard.draw();
        hand.draw();
    }

    private static Card dealTop(Hand h) {
        return h.getCardList().getLast();
    }

    public static void sortHand(Hand hand) {
        hand.getCardList().sort(Comparator
                .comparing((Card c) -> c.getSuit().ordinal())
                .thenComparingInt(c -> ((Rank) c.getRank()).getShortHandValue()));
    }

    // tiny functional helper
    private interface BooleanSupplier { boolean getAsBoolean(); }

    // Simple holders to pass Card out without exposing fields everywhere
    public static class CardHolder { Card card; }

    // Lightweight IO between controller and outer game
    public interface StatusIO {
        int HUMAN();
        void status(String s);
    }

    // in RummyTurnController
    public Card processTopCardFromPile(Hand pile, Hand dest, int thinkingMs) {
        GameGrid.delay(thinkingMs);
        Card card = dealTop(pile);
        pile.remove(card, false);
        pile.draw();
        dest.insert(card, false);
        sortHand(dest);
        dest.draw();
        return card;
    }

    public void discardCardFromHand(Card card, Hand from) {
        card.removeFromHand(false);
        discard.insert(card, false);
        discard.draw();
        from.draw();
    }

    private void setupButtons() {
        ui.addActor(endTurnButton, endTurnLocation);
        ui.addActor(rummyButton, rummyLocation);
        ui.addActor(ginButton, ginLocation);
        ui.addActor(knockButton, knockLocation);

        endTurnButton.addButtonListener(new GGButtonListener() {
            @Override public void buttonPressed(GGButton b) { isEndingTurn = true; }
            @Override public void buttonReleased(GGButton b) {}
            @Override public void buttonClicked(GGButton b) {}
        });
        rummyButton.addButtonListener(new GGButtonListener() {
            @Override public void buttonPressed(GGButton b) { declaredRummy = true; }
            @Override public void buttonReleased(GGButton b) {}
            @Override public void buttonClicked(GGButton b) {}
        });
        ginButton.addButtonListener(new GGButtonListener() {
            @Override public void buttonPressed(GGButton b) { declaredGin = true; }
            @Override public void buttonReleased(GGButton b) {}
            @Override public void buttonClicked(GGButton b) {}
        });
        knockButton.addButtonListener(new GGButtonListener() {
            @Override public void buttonPressed(GGButton b) { declaredKnock = true; }
            @Override public void buttonReleased(GGButton b) {}
            @Override public void buttonClicked(GGButton b) {}
        });

        boolean isGin = (gameMode instanceof GinRummy);

        if (isGin) rummyButton.hide();
        else rummyButton.show();
        if (isGin) ginButton.show();
        else ginButton.hide();
        if (isGin) knockButton.show();
        else knockButton.hide();
    }
}
