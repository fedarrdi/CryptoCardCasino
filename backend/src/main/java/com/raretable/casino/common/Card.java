package com.raretable.casino.common;

public final class Card
{
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank)
    {
        if (suit == null || rank == null)
        {
            throw new IllegalArgumentException("Card suit and rank are required");
        }

        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit()
    {
        return suit;
    }

    public Rank getRank()
    {
        return rank;
    }
}
