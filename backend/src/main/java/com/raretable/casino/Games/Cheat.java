package com.raretable.casino.Games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Iterator;

import com.raretable.casino.Common.Card;
import com.raretable.casino.Common.Player;
import com.raretable.casino.Common.Rank;
import com.raretable.casino.Common.Suit;
import com.raretable.casino.Users.User;

public class Cheat 
{
    private static final int CARDS_PER_PLAYER = 8;

    private final List<Card> cards;
    private final List<Card> pile;
    private final List<Player> players;
    private int turn;
    private CheatGameStatus status;
    private PileEntry lastPlay;
    private UUID winnerId;

    public Cheat(List<User> users)
    {
        this.players = createPlayers(users);
        if (players.isEmpty())
        {
            throw new IllegalArgumentException("Cheat needs at least one player");
        }

        this.pile = new ArrayList<>();
        this.cards = createDeck(players.size());
        this.turn = ThreadLocalRandom.current().nextInt(players.size());
        this.status = CheatGameStatus.IN_PROGRESS;
    }

    public Player removePlayer(Player player)
    {
        Iterator<Player> iterator = players.iterator();

        while (iterator.hasNext())
        {
            Player currentPlayer = iterator.next();

            if (currentPlayer.getUniqueId().equals(player.getUniqueId()))
            {
                iterator.remove();
                return currentPlayer;
            }
        }

        throw new IllegalArgumentException("Player not found: " + player.getUniqueId());
    }

    
    public void shuffle_deck()
    {
        Collections.shuffle(cards);
    }


    public void deal_cards_to_players()
    {
        for (int cardCount = 0; cardCount < CARDS_PER_PLAYER; cardCount++)
        {
            for (Player player : players)
            {
                Card card = cards.remove(cards.size() - 1);
                player.add_card_in_deck(card);
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

    private List<Player> createPlayers(List<User> users)
    {
        List<Player> gamePlayers = new ArrayList<>();

        for (User user : users)
        {
            gamePlayers.add(new Player(user.getUniqueId(), user.getName()));
        }

        return gamePlayers;
    }

    public List<Card> getCardsForPlayer(UUID playerId)
    {
        return getPlayerById(playerId).getCards();
    }

    public CheatGameState getGameState(UUID userId)
    {
        Player requestingPlayer = getPlayerById(userId);
        List<CheatPlayerState> playerStates = new ArrayList<>();

        for (Player player : players)
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

    private Player getCurrentPlayer()
    {
        return players.get(turn);
    }

    private void moveToNextTurn()
    {
        turn = (turn + 1) % players.size();
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
        List<Card> playedCards = new ArrayList<>();

        for (int index : indexesToRemove)
        {
            playedCards.add(player.remove_card_at_index(index));
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

        if (lastPlayer.getCards().isEmpty())
        {
            pile.clear();
            winnerId = lastPlayer.getUniqueId();
            status = CheatGameStatus.FINISHED;
            lastPlay = null;
            return;
        }

        clearPileAfterBluffCall();
        moveToNextTurn();
    }

    public boolean isPlayerTurn(UUID userId)
    {
        return getCurrentPlayer().getUniqueId().equals(userId);
    }

    public CheatGameStatus getStatus()
    {
        return status;
    }

    public UUID getWinnerId()
    {
        return winnerId;
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
            player.add_card_in_deck(card);
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

    private Player getPlayerById(UUID playerId)
    {
        if (playerId == null)
        {
            throw new IllegalArgumentException("Player id is required");
        }

        for (Player player : players)
        {
            if (player.getUniqueId().equals(playerId))
            {
                return player;
            }
        }

        throw new IllegalArgumentException("Player not found: " + playerId);
    }

    private void validateGameInProgress()
    {
        if (status != CheatGameStatus.IN_PROGRESS)
        {
            throw new IllegalStateException("Game is not in progress");
        }
    }

    private void validateCurrentPlayer(UUID userId)
    {
        if (!getCurrentPlayer().getUniqueId().equals(userId))
        {
            throw new IllegalStateException("It is not this player's turn");
        }
    }

}
