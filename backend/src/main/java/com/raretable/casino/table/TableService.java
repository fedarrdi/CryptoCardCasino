package com.raretable.casino.table;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@Service
public final class TableService
{
    private static final Duration CLOSED_TABLE_RETENTION = Duration.ofMinutes(5);

    private final Map<UUID, Table> tables;
    private final UserService userService;

    public TableService(UserService userService)
    {
        this.tables = new ConcurrentHashMap<>();
        this.userService = userService;
    }

    public UUID createTable(GameType gameType, int playersToStart, UUID creatorUserId)
    {
        User creator = userService.getUser(creatorUserId);
        Table table = new Table(gameType, playersToStart, creator);
        tables.put(table.getId(), table);
        return table.getId();
    }

    public void joinTable(UUID tableId, UUID userId)
    {
        Table table = getTable(tableId);
        User user = userService.getUser(userId);

        synchronized (table)
        {
            requireRegisteredTable(tableId, table);
            table.addUser(user);

            if (table.isReadyToStart())
            {
                table.startGame();
            }
        }
    }

    public void leaveTable(UUID tableId, UUID userId)
    {
        Table table = getTable(tableId);

        synchronized (table)
        {
            requireRegisteredTable(tableId, table);

            if (table.getStatus() == TableStatus.WAITING)
            {
                table.removeUser(userId);

                if (table.isCreator(userId))
                {
                    tables.remove(tableId, table);
                }

                return;
            }

            if (table.getStatus() != TableStatus.IN_GAME)
            {
                throw new IllegalStateException("Cannot leave table while status is " + table.getStatus());
            }

            Game game = table.getGame();

            synchronized (game)
            {
                table.forfeitUser(userId);
            }

            if (game.isFinished())
            {
                table.close();
            }
        }
    }

    public void closeTableIfFinished(UUID tableId)
    {
        Table table = getTable(tableId);

        synchronized (table)
        {
            requireRegisteredTable(tableId, table);

            if (table.getStatus() == TableStatus.IN_GAME && table.getGame().isFinished())
            {
                table.close();
            }
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void deleteExpiredClosedTables()
    {
        Instant deletionCutoff = Instant.now().minus(CLOSED_TABLE_RETENTION);
        deleteClosedTablesBefore(deletionCutoff);
    }

    void deleteClosedTablesBefore(Instant deletionCutoff)
    {
        if (deletionCutoff == null)
        {
            throw new IllegalArgumentException("Deletion cutoff is required");
        }

        tables.forEach((tableId, table) ->
        {
            synchronized (table)
            {
                if (table.getStatus() == TableStatus.CLOSED
                    && !table.getClosedAt().isAfter(deletionCutoff))
                {
                    tables.remove(tableId, table);
                }
            }
        });
    }

    public List<GetTableResponse> getAllTables()
    {
        return tables.values()
            .stream()
            .map(this::toTableResponse)
            .toList();
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

    private GetTableResponse toTableResponse(Table table)
    {
        synchronized (table)
        {
            return new GetTableResponse(
                table.getId(),
                table.getGameType(),
                table.getStatus(),
                table.getUsers().size(),
                table.getPlayersToStart()
            );
        }
    }

    private void requireRegisteredTable(UUID tableId, Table table)
    {
        if (tables.get(tableId) != table)
        {
            throw new TableNotFoundException(tableId);
        }
    }
}
