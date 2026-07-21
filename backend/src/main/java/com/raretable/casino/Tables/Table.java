package com.raretable.casino.Tables;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.raretable.casino.Common.TableStatus;
import com.raretable.casino.Games.Cheat;
import com.raretable.casino.Users.User;

public class Table 
{
    private final UUID uniqueId; 
    private List<User> users;
    private Cheat game;
    private TableStatus status;
    private final int players_to_start;


    public Table(int players_to_start)
    {
        this.uniqueId = UUID.randomUUID();
        this.users = new ArrayList<>();
        this.status = TableStatus.WAITING;
        this.players_to_start  = players_to_start;
    }


    public void add_player(User user)
    {
        if (status != TableStatus.WAITING)
        {
            throw new IllegalStateException("Cannot join table while status is " + status);
        }

        if (users.size() >= players_to_start)
        {
            throw new IllegalStateException("Table is full");
        }

        users.add(user);

        if (users.size() == players_to_start)
        {
            game = new Cheat(users);
            game.deal_cards_to_players();
            status = TableStatus.IN_GAME;
        }
    }

}
