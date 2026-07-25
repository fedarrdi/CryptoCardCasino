package com.raretable.casino.game.cheat;

import java.util.UUID;

public class CheatPlayerState
{
    private final UUID playerId;
    private final String name;
    private final int cardCount;
    private final boolean currentTurn;

    public CheatPlayerState(UUID playerId, String name, int cardCount, boolean currentTurn)
    {
        this.playerId = playerId;
        this.name = name;
        this.cardCount = cardCount;
        this.currentTurn = currentTurn;
    }

    public UUID getPlayerId()
    {
        return playerId;
    }

    public String getName()
    {
        return name;
    }

    public int getCardCount()
    {
        return cardCount;
    }

    public boolean isCurrentTurn()
    {
        return currentTurn;
    }
}
