package com.raretable.casino.game.tago;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.raretable.casino.common.Player;
import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

public final class Tago extends Game
{
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 8;

    private static final int CARDS_PER_HAND = 3;

    private final UUID firstPlayerId;
    private final TagoDeck deck;
    private final Map<UUID, TagoHand> hands;
    private final Set<UUID> foldedPlayerIds;
    private final Set<UUID> playersActedThisRound;

    private TagoHand point;
    private Integer pointValueInHalfUnits;
    private boolean playerHiddenCardsRevealed;
    private TagoGameStatus status;
    private List<UUID> winnerIds;
    private Map<UUID, TagoHandScore> scores;

    public Tago(List<User> users)
    {
        this(users, new SecureRandom());
    }

    public Tago(List<User> users, RandomGenerator random)
    {
        super(validateUsers(users));

        if (random == null)
        {
            throw new IllegalArgumentException("Random generator is required");
        }

        selectOfficialFirstPlayer(random);
        this.firstPlayerId = getCurrentPlayer().getUniqueId();
        this.deck = TagoDeck.shuffled(random);
        this.hands = new LinkedHashMap<>();
        this.foldedPlayerIds = new HashSet<>();
        this.playersActedThisRound = new HashSet<>();
        this.status = TagoGameStatus.NOT_STARTED;
        this.winnerIds = List.of();
        this.scores = Map.of();
        dealCards();
    }

    @Override
    public GameType getType()
    {
        return GameType.TAGO;
    }

    @Override
    public boolean isFinished()
    {
        return status == TagoGameStatus.FINISHED;
    }

    private void dealCards()
    {
        if (status != TagoGameStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Cards have already been dealt");
        }

        Map<UUID, List<TagoCard>> dealtCards = new LinkedHashMap<>();

        for (Player player : getPlayers())
        {
            dealtCards.put(player.getUniqueId(), new ArrayList<>());
        }

        List<TagoCard> pointCards = new ArrayList<>();

        for (int cardIndex = 0; cardIndex < CARDS_PER_HAND; cardIndex++)
        {
            for (Player player : getPlayers())
            {
                dealtCards.get(player.getUniqueId()).add(deck.draw());
            }

            pointCards.add(deck.draw());
        }

        for (Player player : getPlayers())
        {
            hands.put(player.getUniqueId(), createHand(dealtCards.get(player.getUniqueId())));
        }

        point = createHand(pointCards);
        status = TagoGameStatus.FIRST_BETTING_ROUND;
        setCurrentPlayer(firstPlayerId);
    }

    public void completeBettingTurn(UUID playerId)
    {
        // Wager accounting can call this after accepting the current player's bet or call.
        validateBettingRound();
        validateActiveCurrentPlayer(playerId);

        playersActedThisRound.add(playerId);
        advanceAfterBettingAction();
    }

    public void fold(UUID playerId)
    {
        validatePlayerActionStatus();
        validateActiveCurrentPlayer(playerId);

        foldedPlayerIds.add(playerId);

        if (getActivePlayerCount() == 1)
        {
            finishWithLastActivePlayer();
            return;
        }

        if (status == TagoGameStatus.POINT_VALUE_SELECTION)
        {
            moveToNextActivePlayer();
            return;
        }

        advanceAfterBettingAction();
    }

    public void choosePointValue(UUID playerId, double pointValue)
    {
        if (status != TagoGameStatus.POINT_VALUE_SELECTION)
        {
            throw new IllegalStateException("The Point does not need a value selection");
        }

        validateActiveCurrentPlayer(playerId);
        int selectedValueInHalfUnits = toHalfUnits(pointValue);

        if (!point.getPossibleValuesInHalfUnits().contains(selectedValueInHalfUnits))
        {
            throw new IllegalArgumentException("Selected value is not valid for the Point");
        }

        pointValueInHalfUnits = selectedValueInHalfUnits;
        status = TagoGameStatus.SECOND_BETTING_ROUND;
        playersActedThisRound.clear();
        resetToFirstActivePlayer();
    }

    public TagoGameStatus getStatus()
    {
        return status;
    }

    public TagoGameState getGameState(UUID viewerId)
    {
        validateCardsDealt();
        getPlayerById(viewerId);

        boolean gameFinished = status == TagoGameStatus.FINISHED;
        UUID currentPlayerId = gameFinished ? null : getCurrentPlayer().getUniqueId();
        Double pointValue = pointValueInHalfUnits == null
            ? null
            : pointValueInHalfUnits / 2.0;
        List<Double> pointValueOptions = status == TagoGameStatus.POINT_VALUE_SELECTION
            ? point.getPossibleValues()
            : List.of();
        List<TagoPlayerState> playerStates = new ArrayList<>();

        for (Player player : getPlayers())
        {
            UUID playerId = player.getUniqueId();
            boolean folded = foldedPlayerIds.contains(playerId);
            boolean revealHiddenCard = playerHiddenCardsRevealed
                && (playerId.equals(viewerId) || (gameFinished && !folded));
            TagoHand hand = hands.get(playerId);

            playerStates.add(new TagoPlayerState(
                playerId,
                player.getName(),
                hand.getVisibleCards(),
                revealHiddenCard ? hand.getHiddenCard() : null,
                folded,
                playerId.equals(currentPlayerId),
                gameFinished ? scores.get(playerId) : null
            ));
        }

        return new TagoGameState(
            status,
            currentPlayerId,
            firstPlayerId,
            getRevealedPointCards(),
            pointValue,
            pointValueOptions,
            playerStates,
            gameFinished ? winnerIds : List.of(),
            deck.size()
        );
    }

    public UUID getCurrentPlayerId()
    {
        if (status == TagoGameStatus.FINISHED)
        {
            throw new IllegalStateException("The game is finished");
        }

        return getCurrentPlayer().getUniqueId();
    }

    public UUID getFirstPlayerId()
    {
        return firstPlayerId;
    }

    public List<TagoCard> getVisibleCardsForPlayer(UUID playerId)
    {
        validateCardsDealt();
        getPlayerById(playerId);
        return hands.get(playerId).getVisibleCards();
    }

    public TagoHand getRevealedHandForPlayer(UUID playerId)
    {
        validateCardsDealt();
        getPlayerById(playerId);

        if (!playerHiddenCardsRevealed)
        {
            throw new IllegalStateException("Player hidden cards have not been revealed");
        }

        return hands.get(playerId);
    }

    public List<TagoCard> getRevealedPointCards()
    {
        validateCardsDealt();

        if (status == TagoGameStatus.FIRST_BETTING_ROUND)
        {
            return point.getVisibleCards();
        }

        return point.getAllCards();
    }

    public List<Double> getPointValueOptions()
    {
        validatePointRevealed();
        return point.getPossibleValues();
    }

    public double getPointValue()
    {
        if (pointValueInHalfUnits == null)
        {
            throw new IllegalStateException("The Point value has not been decided");
        }

        return pointValueInHalfUnits / 2.0;
    }

    public boolean isPlayerFolded(UUID playerId)
    {
        getPlayerById(playerId);
        return foldedPlayerIds.contains(playerId);
    }

    public List<UUID> getWinnerIds()
    {
        if (status != TagoGameStatus.FINISHED)
        {
            throw new IllegalStateException("The game is not finished");
        }

        return Collections.unmodifiableList(winnerIds);
    }

    public TagoHandScore getScoreForPlayer(UUID playerId)
    {
        if (status != TagoGameStatus.FINISHED)
        {
            throw new IllegalStateException("The game is not finished");
        }

        getPlayerById(playerId);
        TagoHandScore score = scores.get(playerId);

        if (score == null)
        {
            throw new IllegalStateException("Player did not reach the showdown");
        }

        return score;
    }

    public int getRemainingDeckSize()
    {
        return deck.size();
    }

    private void selectOfficialFirstPlayer(RandomGenerator random)
    {
        TagoDeck selectionDeck = TagoDeck.shuffled(random);
        Player selectedPlayer = null;
        int closestValueToZero = Integer.MAX_VALUE;

        for (Player player : getPlayers())
        {
            int cardValue = selectionDeck.draw().getValue().getHalfUnits();

            if (cardValue < closestValueToZero)
            {
                selectedPlayer = player;
                closestValueToZero = cardValue;
            }
        }

        setCurrentPlayer(selectedPlayer.getUniqueId());
    }

    private void advanceAfterBettingAction()
    {
        if (allActivePlayersActed())
        {
            advanceBettingRound();
            return;
        }

        moveToNextActivePlayer();
    }

    private void advanceBettingRound()
    {
        playersActedThisRound.clear();

        if (status == TagoGameStatus.FIRST_BETTING_ROUND)
        {
            List<Integer> pointValues = point.getPossibleValuesInHalfUnits();

            if (point.hasPair() && pointValues.size() > 1)
            {
                status = TagoGameStatus.POINT_VALUE_SELECTION;
            }
            else
            {
                pointValueInHalfUnits = pointValues.get(0);
                status = TagoGameStatus.SECOND_BETTING_ROUND;
            }

            resetToFirstActivePlayer();
            return;
        }

        if (status == TagoGameStatus.SECOND_BETTING_ROUND)
        {
            playerHiddenCardsRevealed = true;
            status = TagoGameStatus.THIRD_BETTING_ROUND;
            resetToFirstActivePlayer();
            return;
        }

        finishAtShowdown();
    }

    private void finishAtShowdown()
    {
        if (pointValueInHalfUnits == null)
        {
            throw new IllegalStateException("Point value is required before the showdown");
        }

        Map<UUID, TagoHandScore> calculatedScores = new LinkedHashMap<>();
        List<UUID> calculatedWinners = new ArrayList<>();
        TagoHandScore bestScore = null;

        for (Player player : getPlayers())
        {
            UUID playerId = player.getUniqueId();

            if (foldedPlayerIds.contains(playerId))
            {
                continue;
            }

            TagoHandScore score = TagoHandScore.evaluate(
                hands.get(playerId),
                point,
                pointValueInHalfUnits
            );
            calculatedScores.put(playerId, score);

            if (bestScore == null || score.compareTo(bestScore) > 0)
            {
                bestScore = score;
                calculatedWinners.clear();
                calculatedWinners.add(playerId);
            }
            else if (score.compareTo(bestScore) == 0)
            {
                calculatedWinners.add(playerId);
            }
        }

        scores = Collections.unmodifiableMap(calculatedScores);
        winnerIds = List.copyOf(calculatedWinners);
        status = TagoGameStatus.FINISHED;
    }

    private void finishWithLastActivePlayer()
    {
        for (Player player : getPlayers())
        {
            if (!foldedPlayerIds.contains(player.getUniqueId()))
            {
                winnerIds = List.of(player.getUniqueId());
                scores = Map.of();
                status = TagoGameStatus.FINISHED;
                return;
            }
        }

        throw new IllegalStateException("TAGO has no active player");
    }

    private void moveToNextActivePlayer()
    {
        for (int checkedPlayers = 0; checkedPlayers < getPlayerCount(); checkedPlayers++)
        {
            moveToNextTurn();

            if (!foldedPlayerIds.contains(getCurrentPlayer().getUniqueId()))
            {
                return;
            }
        }

        throw new IllegalStateException("TAGO has no active player");
    }

    private void resetToFirstActivePlayer()
    {
        setCurrentPlayer(firstPlayerId);

        if (foldedPlayerIds.contains(firstPlayerId))
        {
            moveToNextActivePlayer();
        }
    }

    private boolean allActivePlayersActed()
    {
        for (Player player : getPlayers())
        {
            UUID playerId = player.getUniqueId();

            if (!foldedPlayerIds.contains(playerId) && !playersActedThisRound.contains(playerId))
            {
                return false;
            }
        }

        return true;
    }

    private int getActivePlayerCount()
    {
        return getPlayerCount() - foldedPlayerIds.size();
    }

    private TagoHand createHand(List<TagoCard> cards)
    {
        if (cards.size() != CARDS_PER_HAND)
        {
            throw new IllegalArgumentException("A TAGO hand requires exactly three cards");
        }

        return new TagoHand(cards.get(0), cards.get(1), cards.get(2));
    }

    private int toHalfUnits(double value)
    {
        if (!Double.isFinite(value))
        {
            throw new IllegalArgumentException("Point value must be finite");
        }

        double halfUnits = value * 2;

        if (halfUnits != Math.rint(halfUnits))
        {
            throw new IllegalArgumentException("Point value must use increments of 0.5");
        }

        return (int) halfUnits;
    }

    private void validateCardsDealt()
    {
        if (status == TagoGameStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Cards have not been dealt");
        }
    }

    private void validatePointRevealed()
    {
        validateCardsDealt();

        if (status == TagoGameStatus.FIRST_BETTING_ROUND)
        {
            throw new IllegalStateException("The Point has not been fully revealed");
        }
    }

    private void validateBettingRound()
    {
        if (status != TagoGameStatus.FIRST_BETTING_ROUND
            && status != TagoGameStatus.SECOND_BETTING_ROUND
            && status != TagoGameStatus.THIRD_BETTING_ROUND)
        {
            throw new IllegalStateException("TAGO is not in a betting round");
        }
    }

    private void validatePlayerActionStatus()
    {
        if (status != TagoGameStatus.POINT_VALUE_SELECTION)
        {
            validateBettingRound();
        }
    }

    private void validateActiveCurrentPlayer(UUID playerId)
    {
        getPlayerById(playerId);
        validateCurrentPlayer(playerId);

        if (foldedPlayerIds.contains(playerId))
        {
            throw new IllegalStateException("Player has already folded");
        }
    }

    private static List<User> validateUsers(List<User> users)
    {
        if (users == null)
        {
            throw new IllegalArgumentException("Users are required");
        }

        if (users.size() < MIN_PLAYERS || users.size() > MAX_PLAYERS)
        {
            throw new IllegalArgumentException("TAGO needs between 2 and 8 players");
        }

        return users;
    }
}
