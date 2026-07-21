package com.raretable.casino.table;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameFactory;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@Service
public final class TableService
{
    private final Map<UUID, Table> tables;
    private final GameFactory gameFactory;
    private final UserService userService;

    public TableService(GameFactory gameFactory, UserService userService)
    {
        this.tables = new ConcurrentHashMap<>();
        this.gameFactory = gameFactory;
        this.userService = userService;
    }

    public UUID createTable(GameType gameType, int playersToStart)
    {
        gameFactory.validatePlayerCount(gameType, playersToStart);

        Table table = new Table(gameType, playersToStart);
        tables.put(table.getId(), table);
        return table.getId();
    }

    public User joinTable(UUID tableId, UUID userId)
    {
        Table table = getTable(tableId);
        User user = userService.getUser(userId);

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
