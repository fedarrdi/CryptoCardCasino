package com.raretable.casino.table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameType;
import com.raretable.casino.game.cheat.Cheat;
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
    private Instant closedAt;

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

        gameType.validatePlayerCount(playersToStart);

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

        return removeUserById(userId);
    }

    public User forfeitUser(UUID userId)
    {
        if (status != TableStatus.IN_GAME)
        {
            throw new IllegalStateException("Cannot forfeit table while status is " + status);
        }

        User user = getUserById(userId);
        game.forfeitPlayer(userId);
        users.remove(user);
        return user;
    }

    private User removeUserById(UUID userId)
    {
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

    private User getUserById(UUID userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("User id is required");
        }

        for (User user : users)
        {
            if (user.getUniqueId().equals(userId))
            {
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

        closedAt = Instant.now();
        status = TableStatus.CLOSED;
    }

    private Game createGame()
    {
        return switch (gameType)
        {
            case CHEAT -> new Cheat(users);
        };
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

    public Instant getClosedAt()
    {
        if (status != TableStatus.CLOSED)
        {
            throw new IllegalStateException("Table has not been closed");
        }

        return closedAt;
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
