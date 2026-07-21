package com.raretable.casino.table;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameFactory;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

@Service
public final class TableService
{
    private final Map<UUID, Table> tables;
    private final GameFactory gameFactory;

    public TableService(GameFactory gameFactory)
    {
        this.tables = new ConcurrentHashMap<>();
        this.gameFactory = gameFactory;
    }

    public UUID createTable(GameType gameType, int playersToStart)
    {
        gameFactory.validatePlayerCount(gameType, playersToStart);

        Table table = new Table(gameType, playersToStart);
        tables.put(table.getId(), table);
        return table.getId();
    }

    public User joinTable(UUID tableId, String userName)
    {
        Table table = getTable(tableId);
        User user = new User(userName);

        synchronized (table)
        {
            table.addUser(user);

            if (table.isReadyToStart())
            {
                Game game = gameFactory.create(table.getGameType(), table.getUsers());
                table.startGame(game);
            }
        }

        return user;
    }

    public Table getTable(UUID tableId)
    {
        if (tableId == null)
        {
            throw new IllegalArgumentException("Table id is required");
        }

        Table table = tables.get(tableId);

        if (table == null)
        {
            throw new TableNotFoundException(tableId);
        }

        return table;
    }

    public <T extends Game> T requireGame(
        UUID tableId,
        GameType expectedType,
        Class<T> gameClass
    )
    {
        Table table = getTable(tableId);

        synchronized (table)
        {
            if (table.getGameType() != expectedType)
            {
                throw new IllegalStateException(
                    "Table " + tableId + " contains " + table.getGameType() + ", not " + expectedType
                );
            }

            Game game = table.getGame();

            if (!gameClass.isInstance(game))
            {
                throw new IllegalStateException("Stored game does not match table type " + expectedType);
            }

            return gameClass.cast(game);
        }
    }
}
