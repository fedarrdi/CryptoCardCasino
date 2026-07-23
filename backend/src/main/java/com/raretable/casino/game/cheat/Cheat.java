package com.raretable.casino.game.cheat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.common.Card;
import com.raretable.casino.common.Player;
import com.raretable.casino.common.Rank;
import com.raretable.casino.common.Suit;
import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

public final class Cheat extends Game
{
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;

    private static final int CARDS_PER_PLAYER = 8;

    private final List<Card> deck;
    private final List<Card> pile;
    private CheatGameStatus status;
    private PileEntry lastPlay;
    private UUID winnerId;

    public Cheat(List<User> users)
    {
        super(validateUsers(users));
        this.pile = new ArrayList<>();
        this.deck = createDeck(getPlayerCount());
        dealCards();
        selectRandomStartingPlayer();
        this.status = CheatGameStatus.IN_PROGRESS;
    }

    @Override
    public GameType getType()
    {
        return GameType.CHEAT;
    }

    @Override
    public boolean isFinished()
    {
        return status == CheatGameStatus.FINISHED;
    }

    public Player removePlayer(Player player)
    {
        if (player == null)
        {
            throw new IllegalArgumentException("Player is required");
        }

        return removePlayerById(player.getUniqueId());
    }

    public void shuffleDeck()
    {
        Collections.shuffle(deck);
    }

    private void dealCards()
    {
        for (int cardCount = 0; cardCount < CARDS_PER_PLAYER; cardCount++)
        {
            for (Player player : getPlayers())
            {
                Card card = deck.remove(deck.size() - 1);
                player.addCard(card);
            }
        }
    }

    private List<Card> createDeck(int playerCount)
    {
        int totalCardsNeeded = playerCount * CARDS_PER_PLAYER;
        List<Card> deck = new ArrayList<>();

        while (deck.size() < totalCardsNeeded)
        {
            for (Suit suit : Suit.values())
            {
                for (Rank rank : Rank.values())
                {
                    deck.add(new Card(suit, rank));
                }
            }
        }

        Collections.shuffle(deck);

        return new ArrayList<>(deck.subList(0, totalCardsNeeded));
    }

    public List<Card> getCardsForPlayer(UUID playerId)
    {
        return getPlayerById(playerId).getCards();
    }

    public CheatGameState getGameState(UUID userId)
    {
        Player requestingPlayer = getPlayerById(userId);
        List<CheatPlayerState> playerStates = new ArrayList<>();

        for (Player player : getPlayers())
        {
            playerStates.add(new CheatPlayerState(
                player.getUniqueId(),
                player.getName(),
                player.getCards().size(),
                player.getUniqueId().equals(getCurrentPlayer().getUniqueId())
            ));
        }

        UUID lastPlayerId = null;
        Rank lastDeclaredRank = null;

        if (lastPlay != null)
        {
            lastPlayerId = lastPlay.getPlayerId();
            lastDeclaredRank = lastPlay.getDeclaredRank();
        }

        return new CheatGameState(
            requestingPlayer.getCards(),
            playerStates,
            getCurrentPlayer().getUniqueId(),
            status,
            pile.size(),
            lastPlayerId,
            lastDeclaredRank,
            winnerId
        );
    }

    public void playCards(UUID userId, List<Integer> cardIndexes, Rank declaredRank)
    {
        validateGameInProgress();
        validateCurrentPlayer(userId);

        if (declaredRank == null)
        {
            throw new IllegalArgumentException("Declared rank is required");
        }

        Player player = getCurrentPlayer();
        List<Integer> indexesToRemove = getIndexesToRemove(cardIndexes, player);

        if (finishGameIfLastPlayerHasNoCards())
        {
            return;
        }

        List<Card> playedCards = new ArrayList<>();

        for (int index : indexesToRemove)
        {
            playedCards.add(player.removeCardAtIndex(index));
        }

        pile.addAll(playedCards);
        lastPlay = new PileEntry(userId, playedCards, declaredRank);
        moveToNextTurn();
    }

    public void callBluff(UUID callerId)
    {
        validateGameInProgress();

        if (lastPlay == null)
        {
            throw new IllegalStateException("There is no play to call bluff on");
        }

        validateCurrentPlayer(callerId);

        Player caller = getPlayerById(callerId);
        Player lastPlayer = getPlayerById(lastPlay.getPlayerId());

        if (lastPlay.isBluff())
        {
            movePileToPlayer(lastPlayer);
            clearPileAfterBluffCall();
            return;
        }

        movePileToPlayer(caller);

        if (finishGameIfLastPlayerHasNoCards())
        {
            return;
        }

        clearPileAfterBluffCall();
        moveToNextTurn();
    }

    public CheatGameStatus getStatus()
    {
        return status;
    }

    public UUID getWinnerId()
    {
        return winnerId;
    }

    private boolean finishGameIfLastPlayerHasNoCards()
    {
        if (lastPlay == null)
        {
            return false;
        }

        Player lastPlayer = getPlayerById(lastPlay.getPlayerId());

        if (!lastPlayer.getCards().isEmpty())
        {
            return false;
        }

        pile.clear();
        lastPlay = null;
        winnerId = lastPlayer.getUniqueId();
        status = CheatGameStatus.FINISHED;
        return true;
    }

    private void clearPileAfterBluffCall()
    {
        pile.clear();
        lastPlay = null;
    }

    private void movePileToPlayer(Player player)
    {
        for (Card card : pile)
        {
            player.addCard(card);
        }
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

    private void validateGameInProgress()
    {
        if (status != CheatGameStatus.IN_PROGRESS)
        {
            throw new IllegalStateException("Game is not in progress");
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
            throw new IllegalArgumentException("Cheat needs between 2 and 6 players");
        }

        return users;
    }
}
