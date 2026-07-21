package com.raretable.casino.Games.Tago;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class TagoHand
{
    private final TagoCard hiddenCard;
    private final List<TagoCard> visibleCards;

    public TagoHand(TagoCard hiddenCard, TagoCard firstVisibleCard, TagoCard secondVisibleCard)
    {
        if (hiddenCard == null || firstVisibleCard == null || secondVisibleCard == null)
        {
            throw new IllegalArgumentException("A TAGO hand requires exactly three cards");
        }

        this.hiddenCard = hiddenCard;
        this.visibleCards = List.of(firstVisibleCard, secondVisibleCard);
    }

    public TagoCard getHiddenCard()
    {
        return hiddenCard;
    }

    public List<TagoCard> getVisibleCards()
    {
        return visibleCards;
    }

    public List<TagoCard> getAllCards()
    {
        List<TagoCard> cards = new ArrayList<>(3);
        cards.add(hiddenCard);
        cards.addAll(visibleCards);
        return Collections.unmodifiableList(cards);
    }

    public boolean hasPair()
    {
        return getValueCounts().containsValue(2);
    }

    public boolean isThreeOfAKind()
    {
        return getValueCounts().containsValue(3);
    }

    public boolean isZero()
    {
        for (TagoCard card : getAllCards())
        {
            if (card.getValue() != TagoCardValue.ZERO)
            {
                return false;
            }
        }

        return true;
    }

    public boolean hasSameCardsAs(TagoHand other)
    {
        if (other == null)
        {
            throw new IllegalArgumentException("Other hand is required");
        }

        return getValueCounts().equals(other.getValueCounts());
    }

    public List<Double> getPossibleValues()
    {
        List<Double> values = new ArrayList<>();

        for (int halfUnits : getPossibleValuesInHalfUnits())
        {
            values.add(halfUnits / 2.0);
        }

        return Collections.unmodifiableList(values);
    }

    List<Integer> getPossibleValuesInHalfUnits()
    {
        int total = 0;

        for (TagoCard card : getAllCards())
        {
            total += card.getValue().getHalfUnits();
        }

        for (Map.Entry<TagoCardValue, Integer> entry : getValueCounts().entrySet())
        {
            if (entry.getValue() == 2)
            {
                int valueWithoutPair = total - (2 * entry.getKey().getHalfUnits());

                if (valueWithoutPair == total)
                {
                    return List.of(total);
                }

                return List.of(total, valueWithoutPair);
            }
        }

        return List.of(total);
    }

    private Map<TagoCardValue, Integer> getValueCounts()
    {
        Map<TagoCardValue, Integer> counts = new EnumMap<>(TagoCardValue.class);

        for (TagoCard card : getAllCards())
        {
            counts.merge(card.getValue(), 1, Integer::sum);
        }

        return counts;
    }
}
