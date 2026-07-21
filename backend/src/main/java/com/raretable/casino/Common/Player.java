package com.raretable.casino.Common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Player 
{
    private final List<Card> cards;
    private final UUID uniqueId;
    private final String name;
       
    public Player(UUID uniqueId, String name)
    {
        this.cards = new ArrayList<>();
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public Card getCardAtIndex(int index) 
    {
        return cards.get(index);
    }

    public void add_card_in_deck(Card card)
    {
        cards.add(card);
    }

    public Card remove_card_at_index(int index)
    {
        return cards.remove(index);
    }

    public UUID getUniqueId()
    {
        return uniqueId;
    }

    public String getName()
    {
        return name;
    }

    public List<Card> getCards()
    {
        return Collections.unmodifiableList(cards);
    }
}
