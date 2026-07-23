package com.raretable.casino.table;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.game.cheat.Cheat;
import com.raretable.casino.game.tago.Tago;
import com.raretable.casino.game.tienlen.TienLen;
import com.raretable.casino.user.User;

public final class Table
{
    private final UUID id;
    private final UUID creatorUserId;
    private final GameType gameType;
    private final int playersToStart;
    private final List<User> users;

    private Game game;
    private TableStatus status;

    public Table(GameType gameType, int playersToStart, User creator)
    {
        if (gameType == null)
        {
            throw new IllegalArgumentException("Game type is required");
        }

        if (creator == null)
        {
            throw new IllegalArgumentException("Table creator is required");
        }

        validatePlayerCount(gameType, playersToStart);

        this.id = UUID.randomUUID();
        this.creatorUserId = creator.getUniqueId();
        this.gameType = gameType;
        this.playersToStart = playersToStart;
        this.users = new ArrayList<>();
        this.status = TableStatus.WAITING;
        this.users.add(creator);
    }

    public void addUser(User user)
    {
        if (user == null)
        {
            throw new IllegalArgumentException("User is required");
        }

        if (status != TableStatus.WAITING)
        {
            throw new IllegalStateException("Cannot join table while status is " + status);
        }

        if (users.size() >= playersToStart)
        {
            throw new IllegalStateException("Table is full");
        }

        for (User seatedUser : users)
        {
            if (seatedUser.getUniqueId().equals(user.getUniqueId()))
            {
                throw new IllegalStateException("User has already joined the table");
            }
        }

        users.add(user);
    }

    public User removeUser(UUID userId)
    {
        if (status != TableStatus.WAITING)
        {
            throw new IllegalStateException("Cannot leave table while status is " + status);
        }

        Iterator<User> iterator = users.iterator();

        while (iterator.hasNext())
        {
            User user = iterator.next();

            if (user.getUniqueId().equals(userId))
            {
                iterator.remove();
                return user;
            }
        }

        throw new IllegalArgumentException("User not found: " + userId);
    }

    public boolean isReadyToStart()
    {
        return status == TableStatus.WAITING && users.size() == playersToStart;
    }

    public boolean isCreator(UUID userId)
    {
        return creatorUserId.equals(userId);
    }

    public void startGame()
    {
        if (!isReadyToStart())
        {
            throw new IllegalStateException("Table is not ready to start");
        }

        this.game = createGame();
        this.status = TableStatus.IN_GAME;
    }

    public void close()
    {
        if (status != TableStatus.IN_GAME)
        {
            throw new IllegalStateException("Cannot close table while status is " + status);
        }

        if (!game.isFinished())
        {
            throw new IllegalStateException("Cannot close table before the game finishes");
        }

        status = TableStatus.CLOSED;
    }

    private Game createGame()
    {
        return switch (gameType)
        {
            case CHEAT -> new Cheat(users);
            case TAGO -> new Tago(users);
            case TIEN_LEN -> new TienLen(users);
        };
    }

    private static void validatePlayerCount(GameType gameType, int playerCount)
    {
        switch (gameType)
        {
            case CHEAT -> validatePlayerCount(
                playerCount,
                Cheat.MIN_PLAYERS,
                Cheat.MAX_PLAYERS,
                "Cheat"
            );
            case TAGO -> validatePlayerCount(
                playerCount,
                Tago.MIN_PLAYERS,
                Tago.MAX_PLAYERS,
                "TAGO"
            );
            case TIEN_LEN -> validatePlayerCount(
                playerCount,
                TienLen.MIN_PLAYERS,
                TienLen.MAX_PLAYERS,
                "Tien Len"
            );
        }
    }

    private static void validatePlayerCount(
        int playerCount,
        int minimumPlayers,
        int maximumPlayers,
        String gameName
    )
    {
        if (playerCount < minimumPlayers || playerCount > maximumPlayers)
        {
            throw new IllegalArgumentException(
                gameName + " needs between " + minimumPlayers + " and " + maximumPlayers + " players"
            );
        }
    }

    public UUID getId()
    {
        return id;
    }

    public GameType getGameType()
    {
        return gameType;
    }

    public TableStatus getStatus()
    {
        return status;
    }

    public int getPlayersToStart()
    {
        return playersToStart;
    }

    public List<User> getUsers()
    {
        return List.copyOf(users);
    }

    public Game getGame()
    {
        if (game == null)
        {
            throw new IllegalStateException("Game has not started");
        }

        return game;
    }
}
