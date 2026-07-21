package com.raretable.casino.Common;

public class Card 
{
    private Suit suit;
    private Rank rank;  

    public Card(Suit suit, Rank rank) 
    {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit get_suit()
    {
         return suit;
    }

    public Rank get_rank()
    {
        return rank;
    }


}
