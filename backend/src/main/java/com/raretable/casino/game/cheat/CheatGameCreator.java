package com.raretable.casino.game.cheat;

import java.util.List;

import org.springframework.stereotype.Component;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameCreator;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

@Component
public final class CheatGameCreator implements GameCreator
{
    @Override
    public GameType getType()
    {
        return GameType.CHEAT;
    }

    @Override
    public int getMinimumPlayers()
    {
        return Cheat.MIN_PLAYERS;
    }

    @Override
    public int getMaximumPlayers()
    {
        return Cheat.MAX_PLAYERS;
    }

    @Override
    public Game create(List<User> users)
    {
        Cheat game = new Cheat(users);
        game.dealCardsToPlayers();
        return game;
    }
}
