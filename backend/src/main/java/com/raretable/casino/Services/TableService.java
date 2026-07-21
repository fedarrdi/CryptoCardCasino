package com.raretable.casino.Services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raretable.casino.Common.Card;
import com.raretable.casino.Games.Cheat;
import com.raretable.casino.Tables.Table;
import com.raretable.casino.Users.User;

@Service
public class TableService
{
    private final Map<UUID, Table> tables = new HashMap<>();

    public UUID create_table(int players_to_start)
    {
        Table table = new Table(players_to_start);
        tables.put(table.get_table_id(), table);

        return table.get_table_id();
    }

    public User join_table(UUID tableId, User user)
    {
        Table table = get_table(tableId);

        table.add_player(user);

        return user;
    }

    public List<Card> get_player_cards(UUID tableId, UUID userId)
    {
        Table table = get_table(tableId);
        Cheat game = table.get_game();

        if (game == null)
        {
            throw new IllegalStateException("Game has not started");
        }

        return game.getCardsForPlayer(userId);
    }

    private Table get_table(UUID tableId)
    {
        Table table = tables.get(tableId);

        if (table == null)
        {
            throw new IllegalArgumentException("Table not found: " + tableId);
        }

        return table;
    }
}
