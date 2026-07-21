package com.raretable.casino.Requests;

import java.util.List;

import com.raretable.casino.Common.Rank;

public class PlayCardsRequest
{
    private List<Integer> cardIndexes;
    private Rank declaredRank;

    public List<Integer> getCardIndexes()
    {
        return cardIndexes;
    }

    public void setCardIndexes(List<Integer> cardIndexes)
    {
        this.cardIndexes = cardIndexes;
    }

    public Rank getDeclaredRank()
    {
        return declaredRank;
    }

    public void setDeclaredRank(Rank declaredRank)
    {
        this.declaredRank = declaredRank;
    }
}
