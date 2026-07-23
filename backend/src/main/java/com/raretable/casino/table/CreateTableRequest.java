package com.raretable.casino.table;

import java.util.UUID;

import com.raretable.casino.game.GameType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateTableRequest(
    @NotNull GameType gameType,
    @Min(1) int playersToStart,
    @NotNull UUID creatorUserId
)
{
}
