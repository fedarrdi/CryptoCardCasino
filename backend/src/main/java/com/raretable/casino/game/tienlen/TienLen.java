package com.raretable.casino.game.tienlen;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.raretable.casino.common.Card;
import com.raretable.casino.common.Player;
import com.raretable.casino.common.Rank;
import com.raretable.casino.common.Suit;
import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

public final class TienLen extends Game
{
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;

    private static final int CARDS_PER_PLAYER = 13;

    private final List<Card> deck;
    private final Set<UUID> passedPlayerIds;
    private final RandomGenerator random;

    private TienLenGameStatus status;
    private TienLenCombination lastPlay;
    private UUID lastPlayerId;
    private UUID winnerId;

    public TienLen(List<User> users)
    {
        this(users, new SecureRandom());
    }

    public TienLen(List<User> users, RandomGenerator random)
    {
        super(validateUsers(users));

        if (random == null)
        {
            throw new IllegalArgumentException("Random generator is required");
        }

        this.random = random;
        this.deck = createShuffledDeck(random);
        this.passedPlayerIds = new HashSet<>();
        this.status = TienLenGameStatus.NOT_STARTED;
    }

    @Override
    public GameType getType()
    {
        return GameType.TIEN_LEN;
    }

    public void dealCards()
    {
        if (status != TienLenGameStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Cards have already been dealt");
        }

        for (int cardCount = 0; cardCount < CARDS_PER_PLAYER; cardCount++)
        {
            for (Player player : getPlayers())
            {
                player.addCard(deck.remove(deck.size() - 1));
            }
        }

        setCurrentPlayer(getPlayers().get(random.nextInt(getPlayerCount())).getUniqueId());
        status = TienLenGameStatus.IN_PROGRESS;
    }

    public TienLenGameState getGameState(UUID viewerId)
    {
        validateCardsDealt();
        Player requestingPlayer = getPlayerById(viewerId);
        UUID currentPlayerId = status == TienLenGameStatus.FINISHED
            ? null
            : getCurrentPlayer().getUniqueId();
        List<TienLenPlayerState> playerStates = new ArrayList<>();

        for (Player player : getPlayers())
        {
            UUID playerId = player.getUniqueId();

            playerStates.add(new TienLenPlayerState(
                playerId,
                player.getName(),
                player.getCards().size(),
                playerId.equals(currentPlayerId),
                passedPlayerIds.contains(playerId)
            ));
        }

        return new TienLenGameState(
            requestingPlayer.getCards(),
            playerStates,
            currentPlayerId,
            status,
            lastPlay,
            lastPlayerId,
            winnerId
        );
    }

    public void playCards(
        UUID playerId,
        List<Integer> cardIndexes,
        TienLenCombinationType combinationType
    )
    {
        validateGameInProgress();
        validateCurrentPlayer(playerId);

        if (combinationType == null)
        {
            throw new IllegalArgumentException("Combination type is required");
        }

        Player player = getCurrentPlayer();
        List<Integer> indexesToRemove = getIndexesToRemove(cardIndexes, player);
        List<Card> selectedCards = getSelectedCards(player, indexesToRemove);
        TienLenCombination combination = TienLenCombination.create(selectedCards, combinationType);

        if (lastPlay != null && !combination.beats(lastPlay))
        {
            throw new IllegalArgumentException("Played combination does not beat the current play");
        }

        for (int index : indexesToRemove)
        {
            player.removeCardAtIndex(index);
        }

        lastPlay = combination;
        lastPlayerId = playerId;
        passedPlayerIds.clear();

        if (player.getCards().isEmpty())
        {
            winnerId = playerId;
            status = TienLenGameStatus.FINISHED;
            return;
        }

        moveToNextRespondingPlayer();
    }

    public void pass(UUID playerId)
    {
        validateGameInProgress();
        validateCurrentPlayer(playerId);

        if (lastPlay == null)
        {
            throw new IllegalStateException("Cannot pass when leading a new trick");
        }

        if (playerId.equals(lastPlayerId))
        {
            throw new IllegalStateException("Player cannot pass on their own play");
        }

        passedPlayerIds.add(playerId);

        if (!hasPlayerWhoCanRespond())
        {
            startNewTrickFromLastPlayer();
            return;
        }

        moveToNextRespondingPlayer();
    }

    public TienLenGameStatus getStatus()
    {
        return status;
    }

    public UUID getWinnerId()
    {
        return winnerId;
    }

    private void moveToNextRespondingPlayer()
    {
        for (int checkedPlayers = 0; checkedPlayers < getPlayerCount(); checkedPlayers++)
        {
            moveToNextTurn();
            UUID currentPlayerId = getCurrentPlayer().getUniqueId();

            if (!currentPlayerId.equals(lastPlayerId) && !passedPlayerIds.contains(currentPlayerId))
            {
                return;
            }
        }

        throw new IllegalStateException("No player can respond");
    }

    private boolean hasPlayerWhoCanRespond()
    {
        for (Player player : getPlayers())
        {
            UUID playerId = player.getUniqueId();

            if (!playerId.equals(lastPlayerId) && !passedPlayerIds.contains(playerId))
            {
                return true;
            }
        }

        return false;
    }

    private void startNewTrickFromLastPlayer()
    {
        UUID nextLeaderId = lastPlayerId;
        lastPlay = null;
        lastPlayerId = null;
        passedPlayerIds.clear();
        setCurrentPlayer(nextLeaderId);
    }

    private List<Card> getSelectedCards(Player player, List<Integer> indexesToRemove)
    {
        List<Card> selectedCards = new ArrayList<>();

        for (int index : indexesToRemove)
        {
            selectedCards.add(player.getCardAtIndex(index));
        }

        return selectedCards;
    }

    private List<Integer> getIndexesToRemove(List<Integer> cardIndexes, Player player)
    {
        if (cardIndexes == null || cardIndexes.isEmpty())
        {
            throw new IllegalArgumentException("At least one card index is required");
        }

        List<Integer> sortedIndexes = new ArrayList<>(cardIndexes);

        for (Integer cardIndex : sortedIndexes)
        {
            if (cardIndex == null)
            {
                throw new IllegalArgumentException("Card index cannot be null");
            }
        }

        sortedIndexes.sort(Comparator.reverseOrder());

        for (int index = 0; index < sortedIndexes.size(); index++)
        {
            int cardIndex = sortedIndexes.get(index);

            if (cardIndex < 0 || cardIndex >= player.getCards().size())
            {
                throw new IndexOutOfBoundsException("Card index out of bounds: " + cardIndex);
            }

            if (index > 0 && sortedIndexes.get(index).equals(sortedIndexes.get(index - 1)))
            {
                throw new IllegalArgumentException("Duplicate card index: " + cardIndex);
            }
        }

        return sortedIndexes;
    }

    private void validateCardsDealt()
    {
        if (status == TienLenGameStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Cards have not been dealt");
        }
    }

    private void validateGameInProgress()
    {
        if (status != TienLenGameStatus.IN_PROGRESS)
        {
            throw new IllegalStateException("Game is not in progress");
        }
    }

    private static List<Card> createShuffledDeck(RandomGenerator random)
    {
        List<Card> cards = new ArrayList<>();

        for (Suit suit : Suit.values())
        {
            for (Rank rank : Rank.values())
            {
                cards.add(new Card(suit, rank));
            }
        }

        for (int index = cards.size() - 1; index > 0; index--)
        {
            Collections.swap(cards, index, random.nextInt(index + 1));
        }

        return cards;
    }

    private static List<User> validateUsers(List<User> users)
    {
        if (users == null)
        {
            throw new IllegalArgumentException("Users are required");
        }

        if (users.size() < MIN_PLAYERS || users.size() > MAX_PLAYERS)
        {
            throw new IllegalArgumentException("Tien Len needs between 2 and 4 players");
        }

        return users;
    }
}
