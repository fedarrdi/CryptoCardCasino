package com.raretable.casino.Games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.Common.Card;
import com.raretable.casino.Common.Rank;

public class CheatGameState
{
    private final List<Card> cards;
    private final List<CheatPlayerState> players;
    private final UUID currentPlayerId;
    private final CheatGameStatus status;
    private final int pileSize;
    private final UUID lastPlayerId;
    private final Rank lastDeclaredRank;
    private final UUID winnerId;

    public CheatGameState(
        List<Card> cards,
        List<CheatPlayerState> players,
        UUID currentPlayerId,
        CheatGameStatus status,
        int pileSize,
        UUID lastPlayerId,
        Rank lastDeclaredRank,
        UUID winnerId
    )
    {
        this.cards = new ArrayList<>(cards);
        this.players = new ArrayList<>(players);
        this.currentPlayerId = currentPlayerId;
        this.status = status;
        this.pileSize = pileSize;
        this.lastPlayerId = lastPlayerId;
        this.lastDeclaredRank = lastDeclaredRank;
        this.winnerId = winnerId;
    }

    public List<Card> getCards()
    {
        return Collections.unmodifiableList(cards);
    }

    public List<CheatPlayerState> getPlayers()
    {
        return Collections.unmodifiableList(players);
    }

    public UUID getCurrentPlayerId()
    {
        return currentPlayerId;
    }

    public CheatGameStatus getStatus()
    {
        return status;
    }

    public int getPileSize()
    {
        return pileSize;
    }

    public UUID getLastPlayerId()
    {
        return lastPlayerId;
    }

    public Rank getLastDeclaredRank()
    {
        return lastDeclaredRank;
    }

    public UUID getWinnerId()
    {
        return winnerId;
    }
}
