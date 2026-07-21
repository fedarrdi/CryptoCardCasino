package com.raretable.casino.Games.Tago;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class TagoDeckTests
{
    @Test
    void deckContainsThreeCardsOfEveryValue()
    {
        TagoDeck deck = TagoDeck.shuffled(new Random(1));
        Map<TagoCardValue, Integer> valueCounts = new EnumMap<>(TagoCardValue.class);
        int cardCount = 0;

        while (deck.size() > 0)
        {
            TagoCard card = deck.draw();
            valueCounts.merge(card.getValue(), 1, Integer::sum);
            cardCount++;
        }

        assertEquals(30, cardCount);

        for (TagoCardValue value : TagoCardValue.values())
        {
            assertEquals(3, valueCounts.get(value));
        }
    }
}
