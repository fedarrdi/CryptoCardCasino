package com.raretable.casino.game.tago;

import java.util.List;
import java.util.UUID;

public record TagoPlayerState(
    UUID playerId,
    String name,
    List<TagoCard> visibleCards,
    TagoCard hiddenCard,
    boolean folded,
    boolean currentTurn,
    TagoHandScore score
)
{
    public TagoPlayerState
    {
        visibleCards = List.copyOf(visibleCards);
    }
}
