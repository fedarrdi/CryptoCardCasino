package com.raretable.casino.game.tago;

import java.util.List;

import org.springframework.stereotype.Component;

import com.raretable.casino.game.Game;
import com.raretable.casino.game.GameCreator;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;

@Component
public final class TagoGameCreator implements GameCreator
{
    @Override
    public GameType getType()
    {
        return GameType.TAGO;
    }

    @Override
    public int getMinimumPlayers()
    {
        return Tago.MIN_PLAYERS;
    }

    @Override
    public int getMaximumPlayers()
    {
        return Tago.MAX_PLAYERS;
    }

    @Override
    public Game create(List<User> users)
    {
        Tago game = new Tago(users);
        game.dealCards();
        return game;
    }
}
