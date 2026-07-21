package com.raretable.casino.game.tienlen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.raretable.casino.common.Card;
import com.raretable.casino.common.Rank;
import com.raretable.casino.common.Suit;

public record TienLenCombination(
    TienLenCombinationType type,
    List<Card> cards,
    Card highestCard,
    int rankValue,
    int cardCount
)
{
    public TienLenCombination
    {
        if (type == null)
        {
            throw new IllegalArgumentException("Combination type is required");
        }

        cards = List.copyOf(cards);
    }

    public static TienLenCombination create(
        List<Card> cards,
        TienLenCombinationType type
    )
    {
        validateCards(cards);
        List<Card> sortedCards = cards.stream()
            .sorted(TienLenCardComparator.INSTANCE)
            .toList();

        int rankValue = switch (type)
        {
            case SINGLE -> validateSingle(sortedCards);
            case PAIR -> validateSameRank(sortedCards, 2, "A pair");
            case TRIPLE -> validateSameRank(sortedCards, 3, "A triple");
            case STRAIGHT -> validateStraight(sortedCards);
            case FLUSH -> validateFlush(sortedCards);
            case FULL_HOUSE -> validateFullHouse(sortedCards);
            case FOUR_OF_A_KIND -> validateSameRank(sortedCards, 4, "Four of a kind");
        };

        return new TienLenCombination(
            type,
            sortedCards,
            TienLenCardComparator.highestCard(sortedCards),
            rankValue,
            sortedCards.size()
        );
    }

    public boolean beats(TienLenCombination other)
    {
        if (other == null)
        {
            throw new IllegalArgumentException("Combination to beat is required");
        }

        if (type != other.type)
        {
            return false;
        }

        if ((type == TienLenCombinationType.STRAIGHT || type == TienLenCombinationType.FLUSH)
            && cardCount != other.cardCount)
        {
            return false;
        }

        int rankComparison = Integer.compare(rankValue, other.rankValue);

        if (rankComparison != 0)
        {
            return rankComparison > 0;
        }

        return TienLenCardComparator.INSTANCE.compare(highestCard, other.highestCard) > 0;
    }

    private static int validateSingle(List<Card> cards)
    {
        if (cards.size() != 1)
        {
            throw new IllegalArgumentException("A single needs exactly one card");
        }

        return TienLenCardComparator.rankValue(cards.get(0).getRank());
    }

    private static int validateSameRank(List<Card> cards, int requiredSize, String label)
    {
        if (cards.size() != requiredSize)
        {
            throw new IllegalArgumentException(label + " needs exactly " + requiredSize + " cards");
        }

        Rank rank = cards.get(0).getRank();

        for (Card card : cards)
        {
            if (card.getRank() != rank)
            {
                throw new IllegalArgumentException(label + " must use cards with the same rank");
            }
        }

        return TienLenCardComparator.rankValue(rank);
    }

    private static int validateStraight(List<Card> cards)
    {
        if (cards.size() < 3)
        {
            throw new IllegalArgumentException("A straight needs at least three cards");
        }

        List<Integer> rankValues = new ArrayList<>();

        for (Card card : cards)
        {
            if (card.getRank() == Rank.TWO)
            {
                throw new IllegalArgumentException("A straight cannot include a two");
            }

            rankValues.add(TienLenCardComparator.rankValue(card.getRank()));
        }

        rankValues.sort(Comparator.naturalOrder());

        for (int index = 1; index < rankValues.size(); index++)
        {
            if (rankValues.get(index).equals(rankValues.get(index - 1)))
            {
                throw new IllegalArgumentException("A straight cannot contain duplicate ranks");
            }

            if (rankValues.get(index) != rankValues.get(index - 1) + 1)
            {
                throw new IllegalArgumentException("A straight must use consecutive ranks");
            }
        }

        return rankValues.get(rankValues.size() - 1);
    }

    private static int validateFlush(List<Card> cards)
    {
        if (cards.size() != 5)
        {
            throw new IllegalArgumentException("A flush needs exactly five cards");
        }

        Suit suit = cards.get(0).getSuit();

        for (Card card : cards)
        {
            if (card.getSuit() != suit)
            {
                throw new IllegalArgumentException("A flush must use one suit");
            }
        }

        return TienLenCardComparator.rankValue(TienLenCardComparator.highestCard(cards).getRank());
    }

    private static int validateFullHouse(List<Card> cards)
    {
        if (cards.size() != 5)
        {
            throw new IllegalArgumentException("A full house needs exactly five cards");
        }

        Map<Rank, Integer> rankCounts = countRanks(cards);
        Rank tripleRank = null;
        boolean hasPair = false;

        for (Map.Entry<Rank, Integer> entry : rankCounts.entrySet())
        {
            if (entry.getValue() == 3)
            {
                tripleRank = entry.getKey();
            }
            else if (entry.getValue() == 2)
            {
                hasPair = true;
            }
        }

        if (tripleRank == null || !hasPair || rankCounts.size() != 2)
        {
            throw new IllegalArgumentException("A full house needs one triple and one pair");
        }

        return TienLenCardComparator.rankValue(tripleRank);
    }

    private static Map<Rank, Integer> countRanks(List<Card> cards)
    {
        Map<Rank, Integer> rankCounts = new EnumMap<>(Rank.class);

        for (Card card : cards)
        {
            rankCounts.merge(card.getRank(), 1, Integer::sum);
        }

        return rankCounts;
    }

    private static void validateCards(List<Card> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            throw new IllegalArgumentException("At least one card is required");
        }

        Set<String> seenCards = new HashSet<>();

        for (Card card : cards)
        {
            if (card == null)
            {
                throw new IllegalArgumentException("Cards cannot contain null");
            }

            String cardKey = card.getRank() + "-" + card.getSuit();

            if (!seenCards.add(cardKey))
            {
                throw new IllegalArgumentException("Duplicate card: " + cardKey);
            }
        }
    }
}
