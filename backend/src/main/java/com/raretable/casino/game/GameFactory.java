package com.raretable.casino.game;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.raretable.casino.user.User;

@Component
public final class GameFactory
{
    private final Map<GameType, GameCreator> creators;

    public GameFactory(List<GameCreator> gameCreators)
    {
        if (gameCreators == null || gameCreators.isEmpty())
        {
            throw new IllegalArgumentException("At least one game creator is required");
        }

        this.creators = new EnumMap<>(GameType.class);

        for (GameCreator creator : gameCreators)
        {
            GameCreator existingCreator = creators.put(creator.getType(), creator);

            if (existingCreator != null)
            {
                throw new IllegalStateException("Duplicate creator for game type " + creator.getType());
            }
        }
    }

    public Game create(GameType gameType, List<User> users)
    {
        if (users == null)
        {
            throw new IllegalArgumentException("Users are required");
        }

        validatePlayerCount(gameType, users.size());
        return getCreator(gameType).create(List.copyOf(users));
    }

    public void validatePlayerCount(GameType gameType, int playerCount)
    {
        GameCreator creator = getCreator(gameType);

        if (playerCount < creator.getMinimumPlayers() || playerCount > creator.getMaximumPlayers())
        {
            throw new IllegalArgumentException(
                gameType + " needs between " + creator.getMinimumPlayers()
                    + " and " + creator.getMaximumPlayers() + " players"
            );
        }
    }

    private GameCreator getCreator(GameType gameType)
    {
        if (gameType == null)
        {
            throw new IllegalArgumentException("Game type is required");
        }

        GameCreator creator = creators.get(gameType);

        if (creator == null)
        {
            throw new IllegalArgumentException("Unsupported game type: " + gameType);
        }

        return creator;
    }
}
