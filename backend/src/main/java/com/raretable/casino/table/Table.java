package com.raretable.casino.table;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

public final class Table
{
    private final UUID id;
    private final GameType gameType;
    private final int playersToStart;
    private final List<User> users;

    private Game game;
    private TableStatus status;

    public Table(GameType gameType, int playersToStart)
    {
        if (gameType == null)
        {
            throw new IllegalArgumentException("Game type is required");
        }

        if (playersToStart < 1)
        {
            throw new IllegalArgumentException("Players to start must be positive");
        }

        this.id = UUID.randomUUID();
        this.gameType = gameType;
        this.playersToStart = playersToStart;
        this.users = new ArrayList<>();
        this.status = TableStatus.WAITING;
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

    public void startGame(Game game)
    {
        if (!isReadyToStart())
        {
            throw new IllegalStateException("Table is not ready to start");
        }

        if (game == null)
        {
            throw new IllegalArgumentException("Game is required");
        }

        if (game.getType() != gameType)
        {
            throw new IllegalArgumentException("Game type does not match the table");
        }

        this.game = game;
        this.status = TableStatus.IN_GAME;
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
