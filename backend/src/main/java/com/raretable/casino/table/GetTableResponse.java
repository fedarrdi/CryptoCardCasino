package com.raretable.casino.table;

import java.util.UUID;

import com.raretable.casino.game.GameType;

public record GetTableResponse(
    UUID tableId,
    GameType gameType,
    TableStatus status,
    int playersJoined,
    int playersToStart
) {}