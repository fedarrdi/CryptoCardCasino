package com.raretable.casino.game.tago;

import java.util.List;
import java.util.UUID;

public record TagoGameState(
    TagoGameStatus status,
    UUID currentPlayerId,
    UUID firstPlayerId,
    List<TagoCard> pointCards,
    Double pointValue,
    List<Double> pointValueOptions,
    List<TagoPlayerState> players,
    List<UUID> winnerIds,
    int remainingDeckSize
)
{
    public TagoGameState
    {
        pointCards = List.copyOf(pointCards);
        pointValueOptions = List.copyOf(pointValueOptions);
        players = List.copyOf(players);
        winnerIds = List.copyOf(winnerIds);
    }
}
