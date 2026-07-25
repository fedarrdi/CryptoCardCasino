package com.raretable.casino.table;

import com.raretable.casino.game.GameType;

import jakarta.validation.constraints.NotNull;

public record CreateTableRequest(
    @NotNull GameType gameType,
    int playersToStart
)
{
}
