package com.raretable.casino.game.cheat;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raretable.casino.common.Rank;
import com.raretable.casino.game.GameType;
import com.raretable.casino.table.TableService;

@Service
public final class CheatGameService
{
    private final TableService tableService;

    public CheatGameService(TableService tableService)
    {
        this.tableService = tableService;
    }

    public CheatGameState getGameState(UUID tableId, UUID userId)
    {
        Cheat game = getGame(tableId);

        synchronized (game)
        {
            return game.getGameState(userId);
        }
    }

    public CheatGameState playCards(
        UUID tableId,
        UUID userId,
        List<Integer> cardIndexes,
        Rank declaredRank
    )
    {
        Cheat game = getGame(tableId);
        CheatGameState state;

        synchronized (game)
        {
            game.playCards(userId, cardIndexes, declaredRank);
            state = game.getGameState(userId);
        }

        tableService.closeTableIfFinished(tableId);
        return state;
    }

    public CheatGameState callBluff(UUID tableId, UUID userId)
    {
        Cheat game = getGame(tableId);
        CheatGameState state;

        synchronized (game)
        {
            game.callBluff(userId);
            state = game.getGameState(userId);
        }

        tableService.closeTableIfFinished(tableId);
        return state;
    }

    private Cheat getGame(UUID tableId)
    {
        return tableService.requireGame(tableId, GameType.CHEAT, Cheat.class);
    }
}
