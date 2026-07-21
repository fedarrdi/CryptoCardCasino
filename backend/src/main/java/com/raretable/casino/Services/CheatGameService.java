package com.raretable.casino.Services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raretable.casino.Common.Rank;
import com.raretable.casino.Games.Cheat;
import com.raretable.casino.Games.CheatGameState;
import com.raretable.casino.Tables.Table;

@Service
public class CheatGameService
{
    private final TableService tableService;

    public CheatGameService(TableService tableService)
    {
        this.tableService = tableService;
    }

    public CheatGameState get_game_state(UUID tableId, UUID userId)
    {
        Cheat game = get_game(tableId);
        return game.getGameState(userId);
    }

    public CheatGameState play_cards(UUID tableId, UUID userId, List<Integer> cardIndexes, Rank declaredRank)
    {
        Cheat game = get_game(tableId);

        game.playCards(userId, cardIndexes, declaredRank);

        return game.getGameState(userId);
    }

    public CheatGameState call_bluff(UUID tableId, UUID userId)
    {
        Cheat game = get_game(tableId);

        game.callBluff(userId);

        return game.getGameState(userId);
    }

    private Cheat get_game(UUID tableId)
    {
        Table table = tableService.get_table(tableId);
        Cheat game = table.get_game();

        if (game == null)
        {
            throw new IllegalStateException("Game has not started");
        }

        return game;
    }
}
