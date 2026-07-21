package com.raretable.casino.game.tago;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

final class TagoDeck
{
    private static final int COPIES_PER_VALUE = 3;

    private final List<TagoCard> cards;

    private TagoDeck(List<TagoCard> cards)
    {
        this.cards = cards;
    }

    static TagoDeck shuffled(RandomGenerator random)
    {
        if (random == null)
        {
            throw new IllegalArgumentException("Random generator is required");
        }

        List<TagoCard> cards = new ArrayList<>();

        for (TagoCardValue value : TagoCardValue.values())
        {
            for (int copy = 0; copy < COPIES_PER_VALUE; copy++)
            {
                cards.add(new TagoCard(value));
            }
        }

        for (int index = cards.size() - 1; index > 0; index--)
        {
            int swapIndex = random.nextInt(index + 1);
            Collections.swap(cards, index, swapIndex);
        }

        return new TagoDeck(cards);
    }

    TagoCard draw()
    {
        if (cards.isEmpty())
        {
            throw new IllegalStateException("TAGO deck is empty");
        }

        return cards.remove(cards.size() - 1);
    }

    int size()
    {
        return cards.size();
    }
}
