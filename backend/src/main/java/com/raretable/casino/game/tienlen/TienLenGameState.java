package com.raretable.casino.game.tienlen;

import java.util.List;
import java.util.UUID;

import com.raretable.casino.common.Card;

public record TienLenGameState(
    List<Card> cards,
    List<TienLenPlayerState> players,
    UUID currentPlayerId,
    TienLenGameStatus status,
    TienLenCombination lastPlay,
    UUID lastPlayerId,
    UUID winnerId
)
{
    public TienLenGameState
    {
        cards = List.copyOf(cards);
        players = List.copyOf(players);
    }
}
