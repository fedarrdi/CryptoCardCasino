package com.raretable.casino.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.raretable.casino.common.Player;
import com.raretable.casino.user.User;

public abstract class Game
{
    private final List<Player> players;
    private int currentPlayerIndex;

    protected Game(List<User> users)
    {
        if (users == null || users.isEmpty())
        {
            throw new IllegalArgumentException("A game needs at least one player");
        }

        this.players = new ArrayList<>();

        for (User user : users)
        {
            if (user == null)
            {
                throw new IllegalArgumentException("A game cannot contain a null user");
            }

            players.add(new Player(user.getUniqueId(), user.getName()));
        }

        this.currentPlayerIndex = 0;
    }

    public abstract GameType getType();

    public abstract boolean isFinished();

    public abstract void forfeitPlayer(UUID playerId);

    protected final List<Player> getPlayers()
    {
        return Collections.unmodifiableList(players);
    }

    protected final int getPlayerCount()
    {
        return players.size();
    }

    protected final Player getCurrentPlayer()
    {
        if (players.isEmpty())
        {
            throw new IllegalStateException("Game has no players");
        }

        return players.get(currentPlayerIndex);
    }

    protected final Player getPlayerById(UUID playerId)
    {
        if (playerId == null)
        {
            throw new IllegalArgumentException("Player id is required");
        }

        for (Player player : players)
        {
            if (player.getUniqueId().equals(playerId))
            {
                return player;
            }
        }

        throw new IllegalArgumentException("Player not found: " + playerId);
    }

    protected final void selectRandomStartingPlayer()
    {
        currentPlayerIndex = ThreadLocalRandom.current().nextInt(players.size());
    }

    protected final void setCurrentPlayer(UUID playerId)
    {
        Player player = getPlayerById(playerId);
        currentPlayerIndex = players.indexOf(player);
    }

    protected final void moveToNextTurn()
    {
        if (players.isEmpty())
        {
            throw new IllegalStateException("Game has no players");
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    protected final void validateCurrentPlayer(UUID playerId)
    {
        if (!getCurrentPlayer().getUniqueId().equals(playerId))
        {
            throw new IllegalStateException("It is not this player's turn");
        }
    }

    protected final Player removePlayerById(UUID playerId)
    {
        Player player = getPlayerById(playerId);
        int removedPlayerIndex = players.indexOf(player);
        players.remove(removedPlayerIndex);

        if (players.isEmpty())
        {
            currentPlayerIndex = 0;
        }
        else if (removedPlayerIndex < currentPlayerIndex)
        {
            currentPlayerIndex--;
        }
        else if (currentPlayerIndex >= players.size())
        {
            currentPlayerIndex = 0;
        }

        return player;
    }

    public final boolean isPlayerTurn(UUID playerId)
    {
        return getCurrentPlayer().getUniqueId().equals(playerId);
    }
}
