package com.raretable.casino.game;

public enum GameType
{
    CHEAT(2, 6, "Cheat");

    private final int minimumPlayers;
    private final int maximumPlayers;
    private final String displayName;

    GameType(int minimumPlayers, int maximumPlayers, String displayName)
    {
        this.minimumPlayers = minimumPlayers;
        this.maximumPlayers = maximumPlayers;
        this.displayName = displayName;
    }

    public void validatePlayerCount(int playerCount)
    {
        if (playerCount < minimumPlayers || playerCount > maximumPlayers)
        {
            throw new IllegalArgumentException(
                displayName + " needs between " + minimumPlayers + " and " + maximumPlayers + " players"
            );
        }
    }
}
