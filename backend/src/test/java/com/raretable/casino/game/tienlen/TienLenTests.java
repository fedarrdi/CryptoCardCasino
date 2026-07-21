package com.raretable.casino.game.tienlen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.user.User;

class TienLenTests
{
    @Test
    void dealsThirteenCardsToEveryPlayer()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"), new User("Grace"));
        TienLen game = new TienLen(users, new Random(1));

        game.dealCards();

        TienLenGameState state = game.getGameState(users.get(0).getUniqueId());

        assertEquals(TienLenGameStatus.IN_PROGRESS, game.getStatus());
        assertEquals(13, state.cards().size());
        assertEquals(3, state.players().size());
        assertNotNull(state.currentPlayerId());

        for (TienLenPlayerState playerState : state.players())
        {
            assertEquals(13, playerState.cardCount());
        }
    }

    @Test
    void rejectsPlayerCountsOutsideTheAllowedRange()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> new TienLen(List.of(new User("Solo")), new Random(1))
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new TienLen(
                List.of(
                    new User("Ada"),
                    new User("Linus"),
                    new User("Grace"),
                    new User("Ken"),
                    new User("Barbara")
                ),
                new Random(1)
            )
        );
    }

    @Test
    void onlyCurrentPlayerCanPlay()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        TienLen game = new TienLen(users, new Random(2));
        game.dealCards();

        UUID currentPlayerId = game.getGameState(users.get(0).getUniqueId()).currentPlayerId();
        UUID waitingPlayerId = users.stream()
            .map(User::getUniqueId)
            .filter(userId -> !userId.equals(currentPlayerId))
            .findFirst()
            .orElseThrow();

        assertThrows(
            IllegalStateException.class,
            () -> game.playCards(
                waitingPlayerId,
                List.of(0),
                TienLenCombinationType.SINGLE
            )
        );
    }

    @Test
    void playerCanPlaySingleAndOpponentCanPass()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        TienLen game = new TienLen(users, new Random(3));
        game.dealCards();

        UUID firstPlayerId = game.getGameState(users.get(0).getUniqueId()).currentPlayerId();

        game.playCards(firstPlayerId, List.of(0), TienLenCombinationType.SINGLE);

        TienLenGameState afterPlay = game.getGameState(firstPlayerId);
        UUID secondPlayerId = afterPlay.currentPlayerId();

        assertEquals(12, afterPlay.cards().size());
        assertNotNull(afterPlay.lastPlay());
        assertEquals(TienLenCombinationType.SINGLE, afterPlay.lastPlay().type());
        assertNotEquals(firstPlayerId, secondPlayerId);

        game.pass(secondPlayerId);

        TienLenGameState afterPass = game.getGameState(firstPlayerId);

        assertNull(afterPass.lastPlay());
        assertEquals(firstPlayerId, afterPass.currentPlayerId());
    }

    @Test
    void completesGameWithTwoPlayers()
    {
        assertGameCanBeCompleted(List.of(
            new User("Ada"),
            new User("Linus")
        ));
    }

    @Test
    void completesGameWithFourPlayers()
    {
        assertGameCanBeCompleted(List.of(
            new User("Ada"),
            new User("Linus"),
            new User("Grace"),
            new User("Ken")
        ));
    }

    private void assertGameCanBeCompleted(List<User> users)
    {
        TienLen game = new TienLen(users, new Random(4));
        game.dealCards();

        UUID leaderId = game.getGameState(users.get(0).getUniqueId()).currentPlayerId();

        for (int cardsPlayed = 0; cardsPlayed < 13; cardsPlayed++)
        {
            game.playCards(leaderId, List.of(0), TienLenCombinationType.SINGLE);

            if (cardsPlayed == 12)
            {
                break;
            }

            for (int passedPlayers = 0; passedPlayers < users.size() - 1; passedPlayers++)
            {
                UUID respondingPlayerId = game.getGameState(leaderId).currentPlayerId();

                assertNotEquals(leaderId, respondingPlayerId);
                game.pass(respondingPlayerId);
            }

            TienLenGameState newTrickState = game.getGameState(leaderId);

            assertEquals(leaderId, newTrickState.currentPlayerId());
            assertNull(newTrickState.lastPlay());
        }

        TienLenGameState finishedState = game.getGameState(leaderId);

        assertEquals(TienLenGameStatus.FINISHED, finishedState.status());
        assertEquals(leaderId, finishedState.winnerId());
        assertEquals(0, finishedState.cards().size());
        assertNull(finishedState.currentPlayerId());
    }
}
