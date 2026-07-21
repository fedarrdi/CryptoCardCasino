package com.raretable.casino.game.tienlen;

import java.util.List;

import org.springframework.stereotype.Component;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameCreator;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

@Component
public final class TienLenGameCreator implements GameCreator
{
    @Override
    public GameType getType()
    {
        return GameType.TIEN_LEN;
    }

    @Override
    public int getMinimumPlayers()
    {
        return TienLen.MIN_PLAYERS;
    }

    @Override
    public int getMaximumPlayers()
    {
        return TienLen.MAX_PLAYERS;
    }

    @Override
    public Game create(List<User> users)
    {
        TienLen game = new TienLen(users);
        game.dealCards();
        return game;
    }
}
