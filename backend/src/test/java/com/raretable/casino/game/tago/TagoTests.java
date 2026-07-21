package com.raretable.casino.game.tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.user.User;

class TagoTests
{
    @Test
    void dealsThreeCardsToEveryPlayerAndThreeCardsToThePoint()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        Tago game = new Tago(users, new Random(2));

        game.dealCards();

        assertEquals(TagoGameStatus.FIRST_BETTING_ROUND, game.getStatus());
        assertEquals(2, game.getRevealedPointCards().size());
        assertEquals(2, game.getVisibleCardsForPlayer(users.get(0).getUniqueId()).size());
        assertEquals(21, game.getRemainingDeckSize());
        assertThrows(
            IllegalStateException.class,
            () -> game.getRevealedHandForPlayer(users.get(0).getUniqueId())
        );
    }

    @Test
    void progressesThroughAllThreeBettingRoundsAndFindsTheWinner()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"), new User("Grace"));
        Tago game = new Tago(users, new Random(4));
        game.dealCards();

        completeCurrentRound(game, TagoGameStatus.FIRST_BETTING_ROUND);

        assertEquals(3, game.getRevealedPointCards().size());

        if (game.getStatus() == TagoGameStatus.POINT_VALUE_SELECTION)
        {
            double selectedValue = game.getPointValueOptions().get(0);
            game.choosePointValue(game.getCurrentPlayerId(), selectedValue);
        }

        assertEquals(TagoGameStatus.SECOND_BETTING_ROUND, game.getStatus());
        completeCurrentRound(game, TagoGameStatus.SECOND_BETTING_ROUND);

        assertEquals(TagoGameStatus.THIRD_BETTING_ROUND, game.getStatus());
        assertEquals(3, game.getRevealedHandForPlayer(users.get(0).getUniqueId()).getAllCards().size());

        completeCurrentRound(game, TagoGameStatus.THIRD_BETTING_ROUND);

        assertEquals(TagoGameStatus.FINISHED, game.getStatus());
        assertFalse(game.getWinnerIds().isEmpty());

        for (User user : users)
        {
            game.getScoreForPlayer(user.getUniqueId());
        }
    }

    @Test
    void lastPlayerWhoHasNotFoldedWinsImmediately()
    {
        User firstUser = new User("Ada");
        User secondUser = new User("Linus");
        List<User> users = List.of(firstUser, secondUser);
        Tago game = new Tago(users, new Random(8));
        game.dealCards();

        UUID foldingPlayerId = game.getCurrentPlayerId();
        UUID remainingPlayerId = foldingPlayerId.equals(firstUser.getUniqueId())
            ? secondUser.getUniqueId()
            : firstUser.getUniqueId();

        game.fold(foldingPlayerId);

        assertEquals(TagoGameStatus.FINISHED, game.getStatus());
        assertTrue(game.isPlayerFolded(foldingPlayerId));
        assertEquals(List.of(remainingPlayerId), game.getWinnerIds());
    }

    @Test
    void firstActivePlayerChoosesThePointValueWhenItContainsAPair()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"), new User("Grace"));
        Tago game = new Tago(users, new Random(1));
        game.dealCards();

        completeCurrentRound(game, TagoGameStatus.FIRST_BETTING_ROUND);

        assertEquals(TagoGameStatus.POINT_VALUE_SELECTION, game.getStatus());
        assertEquals(List.of(10.5, 0.5), game.getPointValueOptions());
        assertThrows(
            IllegalArgumentException.class,
            () -> game.choosePointValue(game.getCurrentPlayerId(), 4.0)
        );

        game.choosePointValue(game.getCurrentPlayerId(), 0.5);

        assertEquals(TagoGameStatus.SECOND_BETTING_ROUND, game.getStatus());
        assertEquals(0.5, game.getPointValue());
    }

    @Test
    void rejectsPlayerCountsOutsideTheOfficialLimits()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> new Tago(List.of(new User("Solo")), new Random(1))
        );
    }

    private void completeCurrentRound(Tago game, TagoGameStatus round)
    {
        while (game.getStatus() == round)
        {
            game.completeBettingTurn(game.getCurrentPlayerId());
        }
    }
}
