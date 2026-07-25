package com.raretable.casino.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Player
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

    public void addCard(Card card)
    {
        if (card == null)
        {
            throw new IllegalArgumentException("Card is required");
        }

        cards.add(card);
    }

    public Card removeCardAtIndex(int index)
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
