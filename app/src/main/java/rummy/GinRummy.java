package rummy;

public class GinRummy extends GameMode {
    public GinRummy() {
        super(10, "gin");
    }

    @Override
    protected boolean isKnockAllowed() { return true; }

    @Override
    protected void handleKnockScenario(Rummy game, int knockerIndex) {
        int opponentIndex = (knockerIndex + 1) % game.nbPlayers;

        // Compute deadwood points
        int knockerDeadwood = deadwoodPointsOf(game, knockerIndex);
        int opponentDeadwood = deadwoodPointsOf(game, opponentIndex);

        System.out.println("Knocker deadwood: " + knockerDeadwood);
        System.out.println("Opponent deadwood: " + opponentDeadwood);

        if (knockerDeadwood < opponentDeadwood) {
            int points = opponentDeadwood - knockerDeadwood;
            game.addScore(knockerIndex, points);
            game.setStatus("Player " + knockerIndex + " wins the round by Knock! +"+points+" points");
            game.updateScoreDisplay();
        } else if (knockerDeadwood > opponentDeadwood) {
            int points = knockerDeadwood - opponentDeadwood;
            game.addScore(opponentIndex, points);
            game.setStatus("Player " + opponentIndex + " wins the round! +"+points+" points");
            game.updateScoreDisplay();
        } else {
            game.setStatus("Tie! Both players have the same deadwood. No points awarded.");
            game.updateScoreDisplay();
        }
    }
}