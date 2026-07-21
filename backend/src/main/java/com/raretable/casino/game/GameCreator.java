package com.raretable.casino.game;

import java.util.List;

import com.raretable.casino.user.User;

public interface GameCreator
{
    GameType getType();

    int getMinimumPlayers();

    int getMaximumPlayers();

    Game create(List<User> users);
}
