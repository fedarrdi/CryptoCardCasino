package com.raretable.casino.game.tienlen;

import java.util.UUID;

public record TienLenPlayerState(
    UUID playerId,
    String name,
    int cardCount,
    boolean currentTurn,
    boolean passed
)
{
}
