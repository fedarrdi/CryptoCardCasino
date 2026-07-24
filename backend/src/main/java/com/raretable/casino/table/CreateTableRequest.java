package com.raretable.casino.table;

import com.raretable.casino.game.GameType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateTableRequest(
    @NotNull GameType gameType,
    @Min(1) int playersToStart
)
{
}
