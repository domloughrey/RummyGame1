package rummy;

import ch.aplu.jcardgame.*;
import ch.aplu.jgamegrid.*;

import rummy.ai.SmartComputer;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("serial")
public class Rummy extends CardGame {
    private RummyTurnController controller;

    private final int TARGET_SCORE = 100;  // baseline score to win
    static public final int seed = 30008;
    static final Random random = new Random(seed);
    private final Properties properties;
    private final StringBuilder logResult = new StringBuilder();
    private final List<List<String>> playerAutoMovements = new ArrayList<>();
    private final GameMode gameMode;

    private final String version = "1.0";
    public final int nbPlayers = 2;
    public int nbStartCards = 13;
    private final int handWidth = 400;
    private final int pileWidth = 40;
    private final int cardWidth = 40;
    private int thinkingTime = 300;
    private final MeldAnalyser meldAnalyser = new MeldAnalyser();

    private final Deck deck = new Deck(Suit.values(), Rank.values(), "cover");
    private final Location[] handLocations = {
            new Location(350, 75),
            new Location(350, 625),
    };

    private Hand pack;
    private Hand discard;

    // Flags, similar to declaredRummy
    private boolean declaredRummy = false;
    private boolean declaredGin = false;
    private boolean declaredKnock = false;
    private TextActor packNameActor;
    private TextActor discardNameActor;

    private final Location packLocation = new Location(75, 350);
    private final Location discardLocation = new Location(625, 350);
    private final Location packNameLocation = new Location(30, 280);
    private final Location discardNameLocation = new Location(560, 280);

    private final Location[] scoreLocations = {
            new Location(25, 25),
            new Location(575, 675),
    };

    private final Location[] pileNameLocations = {
            new Location(25, 50),
            new Location(575, 625),
    };

    private final TextActor[] scoreActors = {null, null};
    private final TextActor[] pileNameActors = {null, null, null, null};


    Font bigFont = new Font("Arial", Font.BOLD, 36);
    Font smallFont = new Font("Arial", Font.BOLD, 18);

    private final int COMPUTER_PLAYER_INDEX = 0;
    private final int HUMAN_PLAYER_INDEX = 1;
    private int nextRoundStarter = HUMAN_PLAYER_INDEX;

    private final Location playingLocation = new Location(350, 350);
    private final Location textLocation = new Location(350, 450);
    private int delayTime = 600;
    private Hand[] hands;
    private int currentRound = 0;

    public void setStatus(String string) {
        setStatusText(string);
    }

    private int[] scores = new int[nbPlayers];

    private int[] autoIndexHands = new int[nbPlayers];
    private boolean isAuto = false;
    private Hand playingArea;

    private Card selected;
    private Card drawnCard;

    private boolean computerSmart = false;
    private final SmartComputer ai = new SmartComputer(meldAnalyser, 12345L); // seeded for deterministic logs
    private String aiDeclaredAction = null;

    /**
     * Score Section
     */

    private void initScores() {
        Arrays.fill(scores, 0);

        for (int i = 0; i < nbPlayers; i++) {
            // scores[i] = 0;
            String text = "[P" + i + ": " + scores[i] + "]";
            scoreActors[i] = new TextActor(text, Color.WHITE, bgColor, bigFont);
            addActor(scoreActors[i], scoreLocations[i]);
        }

        pileNameActors[0] = new TextActor("Computer", Color.WHITE, bgColor, smallFont);
        addActor(pileNameActors[0], pileNameLocations[0]);

        pileNameActors[1] = new TextActor("Human", Color.WHITE, bgColor, smallFont);
        addActor(pileNameActors[1], pileNameLocations[1]);
    }

    private void updateScore(int player) {
        removeActor(scoreActors[player]);
        int displayScore = Math.max(scores[player], 0);
        String text = "P" + player + "[" + String.valueOf(displayScore) + "]";
        scoreActors[player] = new TextActor(text, Color.WHITE, bgColor, bigFont);
        addActor(scoreActors[player], scoreLocations[player]);
    }

    private void setupPiles() {
        discard = new Hand(deck);
        RowLayout discardLayout = new RowLayout(discardLocation, pileWidth);
        discardLayout.setRotationAngle(270);
        discard.setView(this, discardLayout);
        discard.draw();
        discardNameActor = new TextActor("Discard Pile", Color.WHITE, bgColor, smallFont);
        addActor(discardNameActor, discardNameLocation);

        RowLayout packLayout = new RowLayout(packLocation, pileWidth);
        packLayout.setRotationAngle(90);
        pack.setView(this, packLayout);
        pack.draw();
        packNameActor = new TextActor("Stockpile", Color.WHITE, bgColor, smallFont);
        addActor(packNameActor, packNameLocation);
    }

    private void initRound() {
        declaredRummy = false;
        declaredGin = false;
        declaredKnock = false;

        // 1) hands + dealing
        hands = new Hand[nbPlayers];
        for (int i = 0; i < nbPlayers; i++) {
            hands[i] = new Hand(deck);
        }
        dealingOut(hands);
        // 2) sort hands
        for (int i = 0; i < nbPlayers; i++) {
            RummyTurnController.sortHand(hands[i]);
        }
        // 3) stockpile order
        arrangeStockpile();
        // 4) playing area + hand views
        playingArea = new Hand(deck);
        playingArea.setView(this, new RowLayout(playingLocation, (playingArea.getNumberOfCards() + 3) * cardWidth));
        playingArea.draw();

        RowLayout[] layouts = new RowLayout[nbPlayers];
        for (int i = 0; i < nbPlayers; i++) {
            layouts[i] = new RowLayout(handLocations[i], handWidth);
            layouts[i].setRotationAngle(i);
            hands[i].setView(this, layouts[i]);
            hands[i].setTargetArea(new TargetArea(playingLocation));
            hands[i].draw();
        }
        // 5) piles (creates discard; attaches views to both piles)
        setupPiles();
        // 6) create controller
        controller = new RummyTurnController(
                this, meldAnalyser, gameMode,
                pack, discard, hands,
                delayTime, thinkingTime
        );
    }



    // return random Card from ArrayList
    public static Card randomCard(ArrayList<Card> list) {
        int x = random.nextInt(list.size());
        return list.get(x);
    }

    private Rank getRankFromString(String cardName) {
        String rankString = cardName.substring(0, cardName.length() - 1);
        Integer rankValue = Integer.parseInt(rankString);

        for (Rank rank : Rank.values()) {
            if (rank.getShortHandValue() == rankValue) {
                return rank;
            }
        }

        return Rank.ACE;
    }

    private Suit getSuitFromString(String cardName) {
        String rankString = cardName.substring(0, cardName.length() - 1);
        String suitString = cardName.substring(cardName.length() - 1, cardName.length());
        Integer rankValue = Integer.parseInt(rankString);

        for (Suit suit : Suit.values()) {
            if (suit.getSuitShortHand().equals(suitString)) {
                return suit;
            }
        }
        return Suit.CLUBS;
    }


    private Card getCardFromList(List<Card> cards, String cardName) {
        Rank existingRank = getRankFromString(cardName);
        Suit existingSuit = getSuitFromString(cardName);
        for (Card card : cards) {
            Suit suit = (Suit) card.getSuit();
            Rank rank = (Rank) card.getRank();
            if (suit.getSuitShortHand().equals(existingSuit.getSuitShortHand())
                    && rank.getShortHandValue() == existingRank.getShortHandValue()) {
                return card;
            }
        }

        return null;
    }

    private void arrangeStockpile() {
        String roundString = "rounds." + currentRound;
        String stockpileKey = roundString + ".stockpile.cards";
        pack.shuffle(false);
        String topCardsValue = properties.getProperty(stockpileKey);
        if (topCardsValue == null) {
            return;
        }
        String[] topCards = topCardsValue.split(",");
        for (int i = topCards.length - 1; i >= 0; i--) {
            String topCard = topCards[i];
            if (topCard.length() <= 1) {
                continue;
            }
            Card card = getCardFromList(pack.getCardList(), topCard);
            List<Card> cardList = pack.getCardList();
            if (card != null) {
                cardList.remove(card);
                cardList.add(card);
            }
        }
    }

    private void dealingOut(Hand[] hands) {
        pack = deck.toHand(false);
        String roundString = "rounds." + currentRound;
        for (int i = 0; i < nbPlayers; i++) {
            String initialCardsKey = roundString + ".players." + i + ".initialcards";
            String initialCardsValue = properties.getProperty(initialCardsKey);
            if (initialCardsValue == null) {
                continue;
            }
            String[] initialCards = initialCardsValue.split(",");
            for (String initialCard : initialCards) {
                if (initialCard.length() <= 1) {
                    continue;
                }
                Card card = getCardFromList(pack.getCardList(), initialCard);
                if (card != null) {
                    card.removeFromHand(false);
                    hands[i].insert(card, false);
                }
            }
        }

        for (int i = 0; i < nbPlayers; i++) {
            int cardsToDealt = nbStartCards - hands[i].getNumberOfCards();
            for (int j = 0; j < cardsToDealt; j++) {
                if (pack.isEmpty()) return;
                Card dealt = randomCard(pack.getCardList());
                dealt.removeFromHand(false);
                hands[i].insert(dealt, false);
            }
        }
    }

    private String cardDescriptionForLog(Card card) {
        Rank cardRank = (Rank) card.getRank();
        Suit cardSuit = (Suit) card.getSuit();
        return cardRank.getCardLog() + cardSuit.getSuitShortHand();
    }

    /**
     * Logging Logic
     * @param player
     * @param discardCard
     * @param pickupCard
     */

    private void addCardPlayedToLog(int player, Card discardCard, Card pickupCard, String action) {
        logResult.append("P" + player + "-");
        logResult.append(cardDescriptionForLog(pickupCard) + "-");
        logResult.append(cardDescriptionForLog(discardCard));
        if (action != null) {
            logResult.append("-" + action);
        }

        logResult.append(",");
    }

    private void addRoundInfoToLog(int roundNumber) {
        logResult.append("\n");
        logResult.append("Round" + roundNumber + ":");
    }

    private void addTurnInfoToLog(int turnNumber) {
        logResult.append("\n");
        logResult.append("Turn" + turnNumber + ":");
    }

    private void addPlayerCardsToLog() {
        logResult.append("\n");
        logResult.append("Initial Cards:");
        for (int i = 0; i < nbPlayers; i++) {
            logResult.append("P" + i + "-");
            logResult.append(convertCardListoString(hands[i]));
        }
    }

    private String convertCardListoString(Hand hand) {
        StringBuilder sb = new StringBuilder();
        sb.append(hand.getCardList().stream().map(card -> {
            Rank rank = (Rank) card.getRank();
            Suit suit = (Suit) card.getSuit();
            return rank.getCardLog() + suit.getSuitShortHand();
        }).collect(Collectors.joining(",")));
        sb.append("-");
        return sb.toString();
    }

    private void addEndOfRoundToLog() {
        logResult.append("Round" + currentRound +  " End:P0-" + scores[0] + ",P1-" + scores[1]);
    }

    private void addEndOfGameToLog(List<Integer> winners) {
        logResult.append("\n");
        logResult.append("Game End:");

        for (int i = 0; i < winners.size(); i++) {
            logResult.append("P").append(winners.get(i));
            if (i < winners.size() - 1) {
                logResult.append(", ");
            }
        }
    }

    public Card getRandomCard(Hand hand) {
        delay(thinkingTime);

        int x = random.nextInt(hand.getCardList().size());
        return hand.getCardList().get(x);
    }

    private void processNonAutoPlaying(int nextPlayer, Hand hand) {
        if (HUMAN_PLAYER_INDEX == nextPlayer) {
            // --- Delegate the entire human turn to the turn controller ---
            RummyTurnController.CardHolder drawn = new RummyTurnController.CardHolder();
            RummyTurnController.CardHolder discarded = new RummyTurnController.CardHolder();

            // Bind the status I/O the controller expects
            RummyTurnController.StatusIO io = new RummyTurnController.StatusIO() {
                @Override public int HUMAN() { return HUMAN_PLAYER_INDEX; }
                @Override public void status(String s) { setStatus(s); }
            };

            // Let the controller run the UI interaction + state mutations
            PlayerAction choice = controller.playHumanTurn(io, drawn, discarded);

            // Keep Rummy's fields/logs exactly as before
            drawnCard = drawn.card;
            selected  = discarded.card;

            String action = (choice == PlayerAction.NONE ? null : choice.name());
            addCardPlayedToLog(nextPlayer, selected, drawnCard, action);

            // Mirror original flags so the existing round loop behaves unchanged
            if (choice == PlayerAction.RUMMY) declaredRummy = true;
            if (choice == PlayerAction.GIN)   declaredGin   = true;
            if (choice == PlayerAction.KNOCK) declaredKnock = true;

        } else {
            // --- Existing computer path (unchanged) ---
            if (computerSmart) {
                ai.playTurn(aiActions, aiPolicy());
                addCardPlayedToLog(nextPlayer, selected, drawnCard, aiDeclaredAction);
            } else {
                setStatusText("Player " + nextPlayer + " thinking...");
                if (!discard.isEmpty()) {
                    boolean isPickingDiscard = new Random().nextBoolean();
                    if (isPickingDiscard) {
                        setStatusText("Player " + nextPlayer + " is picking a card from discard pile...");
                        drawnCard = controller.processTopCardFromPile(discard, hand, thinkingTime);
                    } else {
                        setStatusText("Player " + nextPlayer + " is picking a card from stockpile...");
                        drawnCard = controller.processTopCardFromPile(pack, hands[nextPlayer], thinkingTime);
                    }
                } else {
                    setStatusText("Player " + nextPlayer + " is picking a card from stockpile...");
                    drawnCard = controller.processTopCardFromPile(pack, hands[nextPlayer], thinkingTime);
                }
                selected = getRandomCard(hands[nextPlayer]);
                controller.discardCardFromHand(selected, hand);
                addCardPlayedToLog(nextPlayer, selected, drawnCard, null);
            }
        }
    }


    private List<PlayerAction> getActionFromAutoMovement(String nextMovement) {
        List<PlayerAction> actions = new ArrayList<>();
        String[] movementComponents = nextMovement.split("-");
        switch (movementComponents.length) {
            case 1:
                actions.add(PlayerAction.NONE);
                break;
            case 2:
                actions.add(PlayerAction.valueOf(movementComponents[0]));
                break;
            case 3:
                actions.add(PlayerAction.valueOf(movementComponents[0]));
                actions.add(PlayerAction.valueOf(movementComponents[2]));
                break;
        }
        return actions;
    }

    private Card getCardElementFromAutoMovement(Hand hand, String nextMovement) {
        String[] movementComponents = nextMovement.split("-");
        return switch (movementComponents.length) {
            case 1 -> getCardFromList(hand.getCardList(), movementComponents[0]);
            case 2, 3 -> getCardFromList(hand.getCardList(), movementComponents[1]);
            default -> null;
        };
    }

    private boolean playARound() {
        // int nextPlayer = HUMAN_PLAYER_INDEX;
        int nextPlayer = nextRoundStarter;
        int winnerIndex = -1;
        addRoundInfoToLog(currentRound);
        addPlayerCardsToLog();
        int i = 0;
        boolean isContinue = true;

        setupPlayerAutoMovements();
        while (isContinue) {

            System.out.println("    -- TURN " + i + " --   ");
            System.out.println("Player " + nextPlayer + " Turn");
            addTurnInfoToLog(i);
            i++;

            for (int j = 0; j < nbPlayers; j++) {
                Hand hand = hands[nextPlayer];

                if (isAuto) {
                    int nextPlayerAutoIndex = autoIndexHands[nextPlayer];
                    List<String> nextPlayerMovement = playerAutoMovements.get(nextPlayer);
                    String nextMovement = "";
                    boolean hasRunAuto = false;


                    if (nextPlayerMovement != null && nextPlayerMovement.size() > nextPlayerAutoIndex) {
                        nextMovement = nextPlayerMovement.get(nextPlayerAutoIndex);
                        if (!nextMovement.isEmpty()) {
                            hasRunAuto = true;
                            nextPlayerAutoIndex++;

                            autoIndexHands[nextPlayer] = nextPlayerAutoIndex;

                            System.out.printf("[AUTO-RUN] r=%d p=%d exec='%s'%n", currentRound, nextPlayer, nextMovement);

                            setStatus("Player " + nextPlayer + " is playing");

                            List<PlayerAction> cardActions = getActionFromAutoMovement(nextMovement);
                            PlayerAction cardAction = cardActions.get(0);
                            Card card = null;

                            if (cardAction == PlayerAction.DISCARD) {
                                card = controller.processTopCardFromPile(discard, hand, thinkingTime);
                                System.out.println("Player " + nextPlayer + " picks up: " + card.getRank() + " of " + card.getSuit() + " from Discard");

                                selected = getCardElementFromAutoMovement(hand, nextMovement);
                                System.out.println("Player " + nextPlayer + " discards: " + selected.getRank() + " of " + selected.getSuit());
                                controller.discardCardFromHand(selected, hand);


                            } else if (cardAction == PlayerAction.STOCKPILE) {
                                card = controller.processTopCardFromPile(pack, hand, thinkingTime);
                                System.out.println("Player " + nextPlayer + " picks up: " + card.getRank() + " of " + card.getSuit() + " from Stock");

                                selected = getCardElementFromAutoMovement(hand, nextMovement);
                                System.out.println("Player " + nextPlayer + " discards: " + selected.getRank() + " of " + selected.getSuit());
                                controller.discardCardFromHand(selected, hand);
                            }

                            delay(thinkingTime);
                            if (cardActions.size() > 1) {
                                PlayerAction lastAction = cardActions.get(1);
                                if (lastAction == PlayerAction.RUMMY) {
                                    setStatus("Player " + nextPlayer + " is rummy...");
                                } else if (lastAction == PlayerAction.GIN) {
                                    setStatus("Player " + nextPlayer + " is ginning...");
                                } else if (lastAction == PlayerAction.KNOCK) {
                                    setStatus("Player " + nextPlayer + " is knocking...");
                                }
                                addCardPlayedToLog(nextPlayer, selected, card, lastAction.name());
                                Integer w = gameMode.endRoundTemplate(this, nextPlayer, lastAction);
                                if (w != null) {
                                    winnerIndex = w;
                                    isContinue = false;
                                    break; // only break when the template says the round ends
                                }
                            } else {
                                addCardPlayedToLog(nextPlayer, selected, card, null);
                            }
                            delay(delayTime);
                        }
                    }

                    if (isAuto && !hasRunAuto) {
                        processNonAutoPlaying(nextPlayer, hand);
                    }
                }

                if (!isAuto) {
                    processNonAutoPlaying(nextPlayer, hand);
                }

                if (nextPlayer == COMPUTER_PLAYER_INDEX && aiDeclaredAction != null) {
                    if (aiDeclaredAction.equals("KNOCK")) {
                        gameMode.endRoundTemplate(this, COMPUTER_PLAYER_INDEX, PlayerAction.KNOCK);
                        winnerIndex = COMPUTER_PLAYER_INDEX;
                    } else {
                        gameMode.endRoundTemplate(this, COMPUTER_PLAYER_INDEX, PlayerAction.RUMMY);
                        winnerIndex = COMPUTER_PLAYER_INDEX;
                    }
                    isContinue = false;
                    break;
                }

                if (pack.isEmpty()) {
                    setStatus("Stockpile is exhausted. Calculating players' scores now.");
                    isContinue = false;
                    // gameMode.handleDeclaredScenario(this, -1);
                    gameMode.handleStockpileExhausted(this);
                    break;
                }

                nextPlayer = (nextPlayer + 1) % nbPlayers;
            }
        }

        if (winnerIndex != -1) {
            nextRoundStarter = winnerIndex;
        }

        addEndOfRoundToLog();
        return !checkIfGameOver();
    }

    private void setupPlayerAutoMovements() {
        String roundString = "rounds." + currentRound;
        String player0AutoMovement = properties.getProperty(roundString + ".players.0.cardsPlayed");
        String player1AutoMovement = properties.getProperty(roundString + ".players.1.cardsPlayed");

        String[] playerMovements = new String[]{"", ""};
        if (player0AutoMovement != null) {
            playerMovements[0] = player0AutoMovement;
        }

        if (player1AutoMovement != null) {
            playerMovements[1] = player1AutoMovement;
        }

        for (int i = 0; i < playerMovements.length; i++) {
            String movementString = playerMovements[i];
            List<String> movements = Arrays.asList(movementString.split(","));
            playerAutoMovements.add(movements);
        }
    }

    public String runApp() {
        setTitle("Pinochle  (V" + version + ") Constructed for UofM SWEN30006 with JGameGrid (www.aplu.ch)");
        setStatusText("Initializing...");

        initScores();

        currentRound = 0;
        boolean isContinue = true;

        while(isContinue) {
            System.out.println("===== ROUND " + currentRound + " =====");

            declaredRummy = false;
            declaredGin = false;
            declaredKnock = false;
            aiDeclaredAction = null;

            initRound();

            playerAutoMovements.clear();
            setupPlayerAutoMovements();
            Arrays.fill(autoIndexHands, 0);

            isContinue = playARound();

            for (int i = 0; i < nbPlayers; i++) updateScore(i);

            if (!isContinue) break;
            currentRound++;
        }

        //Check Winner
        for (int i = 0; i < nbPlayers; i++) updateScore(i);
        int maxScore = 0;
        for (int i = 0; i < nbPlayers; i++) if (scores[i] > maxScore) maxScore = scores[i];
        List<Integer> winners = new ArrayList<>();
        for (int i = 0; i < nbPlayers; i++) if (scores[i] == maxScore) winners.add(i);
        String winText;
        if (winners.size() == 1) {
            winText = "Game over. Winner is player: " +
                    winners.iterator().next();
        } else {
            winText = "Game Over. Drawn winners are players: " +
                    String.join(", ", winners.stream().map(String::valueOf).collect(Collectors.toList()));
        }

        System.out.println(winText);


        addActor(new Actor("sprites/gameover.gif"), textLocation);
        setStatusText(winText);
        refresh();
        addEndOfGameToLog(winners);

        System.out.println("\n===== RUMMY LOG =====\n" + logResult);
        return logResult.toString();
    }

    public Rummy(Properties properties) {
        super(700, 700, 30);
        this.properties = properties;
        this.gameMode = GameModeFactory.create(properties.getProperty("mode"));
        isAuto = Boolean.parseBoolean(properties.getProperty("isAuto"));
        thinkingTime = Integer.parseInt(properties.getProperty("thinkingTime"));
        delayTime = Integer.parseInt(properties.getProperty("delayTime"));
        nbStartCards = gameMode.getNumberOfCards();
        nbStartCards = Integer.parseInt(properties.getProperty("number_cards"));
        computerSmart = Boolean.parseBoolean(properties.getProperty("computer_smart"));
    }

    public void addScore(int player, int points) {
        scores[player] += points;
    }

    public void updateScoreDisplay() {
        for (int i = 0; i < nbPlayers; i++) {
            updateScore(i);
        }
    }

    private boolean checkIfGameOver() {
        for (int i = 0; i < nbPlayers; i++) {
            if (scores[i] >= TARGET_SCORE) {
                setStatus("Player " + i + " reached " + TARGET_SCORE + " points and wins the game!");
                return true;  // Game should stop
            }
        }
        return false;  // No one reached target, continue game
    }


    // Policy: Classic = no knock; Gin = knock ≤ 7
    private SmartComputer.DeclarationPolicy aiPolicy() {
        final boolean isGin = (gameMode instanceof GinRummy); // you already have GinRummy/ClassicRummy
        return new SmartComputer.DeclarationPolicy() {
            @Override public boolean isKnockAllowed() { return isGin; }
            @Override public int knockThreshold() { return 7; }
        };
    }

    // Actions: how the AI draws, discards, declares and queries your piles/hands
    private final SmartComputer.Actions aiActions = new SmartComputer.Actions() {
        @Override public void takeFromDiscard() {
            setStatusText("Computer takes from discard...");
            drawnCard = controller.processTopCardFromPile(discard, hands[COMPUTER_PLAYER_INDEX], thinkingTime);
            System.out.println("Computer draws " + drawnCard.getRank() + " of " + drawnCard.getSuit() + " from Discard Pile.");
        }
        @Override public Card drawFromStock() {
            setStatusText("Computer draws from stock...");
            drawnCard = controller.processTopCardFromPile(pack, hands[COMPUTER_PLAYER_INDEX], thinkingTime);
            System.out.println("Computer draws " + drawnCard.getRank() + " of " + drawnCard.getSuit() + " from Stock Pile.");
            return drawnCard;
        }
        @Override public void discard(Card c) {
            System.out.println("Computer discards card: " + c.getRank() + " of " + c.getSuit());
            selected = c; // so logging works the same as human/random paths
            controller.discardCardFromHand(c, hands[COMPUTER_PLAYER_INDEX]);
        }
        @Override public void declareAll() {
            aiDeclaredAction = (gameMode instanceof GinRummy) ? "GIN" : "RUMMY";
            setStatus("Computer declares " + aiDeclaredAction + "!");
        }
        @Override public void declareKnock() {
            aiDeclaredAction = "KNOCK";
            setStatus("Computer declares Knock!");
        }
        @Override public void endTurn() { /* nothing extra */ }
        @Override public Card peekDiscardTop() {
            return discard.isEmpty() ? null : discard.getCardList().get(discard.getCardList().size()-1);
        }
        @Override public Hand getMyHand() {
            return hands[COMPUTER_PLAYER_INDEX];
        }
    };

    public Hand[] getHands() {
        return hands;
    }

    // === template glue ===
    public boolean isStockEmpty() {
        return pack == null || pack.isEmpty();
    }

    public void clearDeclarations() {
        declaredRummy = false;
        declaredGin = false;
        declaredKnock = false;
        aiDeclaredAction = null;
    }

    public void setNextRoundStarter(int idx) {
        nextRoundStarter = idx;
    }

}