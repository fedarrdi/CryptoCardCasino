package com.raretable.casino.Games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Iterator;

import com.raretable.casino.Common.Card;
import com.raretable.casino.Common.Player;
import com.raretable.casino.Common.Rank;
import com.raretable.casino.Common.Suit;

public class Cheat 
{
    private static final int CARDS_PER_PLAYER = 8;

    private final List<Card> cards;
    private List<Card> pile;
    private final List<Player> players;
    private int turn;

    public Cheat(List<Player> players)
    {
        this.players = new ArrayList<>(players);
        this.pile = new ArrayList<>();
        this.cards = createDeck(players.size());
        this.turn = ThreadLocalRandom.current().nextInt(players.size());
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

    public List<Card> getCardsForPlayer(UUID playerId)
    {
        for (Player player : players)
        {
            if (player.getUniqueId().equals(playerId))
            {
                return player.getCards();
            }
        }

        throw new IllegalArgumentException("Player not found: " + playerId);
    }
}
