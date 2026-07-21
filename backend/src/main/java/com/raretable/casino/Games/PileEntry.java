package com.raretable.casino.Games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.Common.Card;
import com.raretable.casino.Common.Rank;

public class PileEntry
{
    private final UUID playerId;
    private final List<Card> cards;
    private final Rank declaredRank;

    public PileEntry(UUID playerId, List<Card> cards, Rank declaredRank)
    {
        this.playerId = playerId;
        this.cards = new ArrayList<>(cards);
        this.declaredRank = declaredRank;
    }

    public UUID getPlayerId()
    {
        return playerId;
    }

    public List<Card> getCards()
    {
        return Collections.unmodifiableList(cards);
    }

    public Rank getDeclaredRank()
    {
        return declaredRank;
    }

    public boolean isBluff()
    {
        for (Card card : cards)
        {
            if (card.get_rank() != declaredRank)
            {
                return true;
            }
        }

        return false;
    }
}
