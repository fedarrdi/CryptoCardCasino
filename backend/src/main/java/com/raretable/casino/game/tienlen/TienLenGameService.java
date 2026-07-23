package com.raretable.casino.game.tienlen;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raretable.casino.game.GameType;
import com.raretable.casino.table.TableService;

@Service
public final class TienLenGameService
{
    private final TableService tableService;

    public TienLenGameService(TableService tableService)
    {
        this.tableService = tableService;
    }

    public TienLenGameState getGameState(UUID tableId, UUID userId)
    {
        TienLen game = getGame(tableId);

        synchronized (game)
        {
            return game.getGameState(userId);
        }
    }

    public TienLenGameState playCards(
        UUID tableId,
        UUID userId,
        List<Integer> cardIndexes,
        TienLenCombinationType combinationType
    )
    {
        TienLen game = getGame(tableId);
        TienLenGameState state;

        synchronized (game)
        {
            game.playCards(userId, cardIndexes, combinationType);
            state = game.getGameState(userId);
        }

        tableService.closeTableIfFinished(tableId);
        return state;
    }

    public TienLenGameState pass(UUID tableId, UUID userId)
    {
        TienLen game = getGame(tableId);
        TienLenGameState state;

        synchronized (game)
        {
            game.pass(userId);
            state = game.getGameState(userId);
        }

        tableService.closeTableIfFinished(tableId);
        return state;
    }

    private TienLen getGame(UUID tableId)
    {
        return tableService.requireGame(tableId, GameType.TIEN_LEN, TienLen.class);
    }
}
