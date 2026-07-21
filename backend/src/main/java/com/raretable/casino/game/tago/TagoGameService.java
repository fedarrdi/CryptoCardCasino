package com.raretable.casino.game.tago;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raretable.casino.game.GameType;
import com.raretable.casino.table.TableService;

@Service
public final class TagoGameService
{
    private final TableService tableService;

    public TagoGameService(TableService tableService)
    {
        this.tableService = tableService;
    }

    public TagoGameState getGameState(UUID tableId, UUID userId)
    {
        Tago game = getGame(tableId);

        synchronized (game)
        {
            return game.getGameState(userId);
        }
    }

    public TagoGameState completeBettingTurn(UUID tableId, UUID userId)
    {
        Tago game = getGame(tableId);

        synchronized (game)
        {
            game.completeBettingTurn(userId);
            return game.getGameState(userId);
        }
    }

    public TagoGameState fold(UUID tableId, UUID userId)
    {
        Tago game = getGame(tableId);

        synchronized (game)
        {
            game.fold(userId);
            return game.getGameState(userId);
        }
    }

    public TagoGameState choosePointValue(UUID tableId, UUID userId, double pointValue)
    {
        Tago game = getGame(tableId);

        synchronized (game)
        {
            game.choosePointValue(userId, pointValue);
            return game.getGameState(userId);
        }
    }

    private Tago getGame(UUID tableId)
    {
        return tableService.requireGame(tableId, GameType.TAGO, Tago.class);
    }
}
