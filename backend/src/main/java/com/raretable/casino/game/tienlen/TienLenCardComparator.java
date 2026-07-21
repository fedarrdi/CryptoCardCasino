package com.raretable.casino.game.tienlen;

import java.util.Comparator;
import java.util.List;

import com.raretable.casino.common.Card;
import com.raretable.casino.common.Rank;
import com.raretable.casino.common.Suit;

final class TienLenCardComparator implements Comparator<Card>
{
    static final TienLenCardComparator INSTANCE = new TienLenCardComparator();

    private TienLenCardComparator()
    {
    }

    @Override
    public int compare(Card left, Card right)
    {
        int rankComparison = Integer.compare(rankValue(left.getRank()), rankValue(right.getRank()));

        if (rankComparison != 0)
        {
            return rankComparison;
        }

        return Integer.compare(suitValue(left.getSuit()), suitValue(right.getSuit()));
    }

    static Card highestCard(List<Card> cards)
    {
        return cards.stream()
            .max(INSTANCE)
            .orElseThrow(() -> new IllegalArgumentException("At least one card is required"));
    }

    static int rankValue(Rank rank)
    {
        return switch (rank)
        {
            case THREE -> 0;
            case FOUR -> 1;
            case FIVE -> 2;
            case SIX -> 3;
            case SEVEN -> 4;
            case EIGHT -> 5;
            case NINE -> 6;
            case TEN -> 7;
            case JACK -> 8;
            case QUEEN -> 9;
            case KING -> 10;
            case ACE -> 11;
            case TWO -> 12;
        };
    }

    private static int suitValue(Suit suit)
    {
        return switch (suit)
        {
            case CLUBS -> 0;
            case DIAMONDS -> 1;
            case HEARTS -> 2;
            case SPADES -> 3;
        };
    }
}
