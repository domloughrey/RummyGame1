package rummy;

import ch.aplu.jcardgame.Card;

import java.util.List;

public abstract class GameMode {
    protected final int numberOfCards;
    protected final String modeName;
    protected final MeldAnalyser meldAnalyser = new MeldAnalyser();

    public GameMode(int numberOfCards, String modeName) {
        this.numberOfCards = numberOfCards;
        this.modeName = modeName;
    }

    public int getNumberOfCards() { return numberOfCards; }

    public final Integer endRoundTemplate(
            Rummy game,
            int currentPlayer,
            PlayerAction lastAction
    ) {
        if (lastAction == null || lastAction == PlayerAction.NONE) return null;

        switch (lastAction) {
            case RUMMY:
            case GIN:
                handleDeclaredScenario(game, currentPlayer); // subclass may override scoring/text
                game.updateScoreDisplay();
                return currentPlayer; // round ends
            case KNOCK:
                if (isKnockAllowed()) {
                    handleKnockScenario(game, currentPlayer);   // Gin overrides scoring/undercut
                    game.updateScoreDisplay();
                    return currentPlayer; // round ends
                } else {
                    game.setStatus("Knock is not allowed in this mode.");
                    return null; // continue
                }
            default:
                return null;
        }
    }

    protected boolean isKnockAllowed() { return false; }

    protected void handleDeclaredScenario(Rummy game, int winnerIndex) {
        int loserIndex = (winnerIndex + 1) % 2;
        int points = deadwoodPointsOf(game, loserIndex);
        game.addScore(winnerIndex, points);
        game.setStatus("Player " + winnerIndex + " wins by declaring Rummy! +" + points + " points");
    }

    protected void handleStockpileExhausted(Rummy game) {
        int humanPoints = deadwoodPointsOf(game, 1);
        int computerPoints = deadwoodPointsOf(game, 0);

        if (humanPoints < computerPoints) {
            game.addScore(1, computerPoints);
            game.setStatus("Stockpile exhausted — Human wins! +" + computerPoints + " points");
        } else if (computerPoints < humanPoints) {
            game.addScore(0, humanPoints);
            game.setStatus("Stockpile exhausted — Computer wins! +" + humanPoints + " points");
        } else {
            game.setStatus("Stockpile exhausted — It's a tie!");
        }
    }

    /**
     * Classic forbids knock; Gin overrides and scores.
     */
    protected void handleKnockScenario(Rummy game, int winnerIndex) {
    }

    // --- helpers ---
    protected int deadwoodPointsOf(Rummy game, int playerIndex) {
        List<Card> fullHand = game.getHands()[playerIndex].getCardList();
        List<Card> deadwoodHand = meldAnalyser.getDeadwood(fullHand);
        return meldAnalyser.getDeadwoodPoints(deadwoodHand);
    }
}
