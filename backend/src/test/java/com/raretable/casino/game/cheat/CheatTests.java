package com.raretable.casino.game.cheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.common.Rank;
import com.raretable.casino.user.User;

class CheatTests
{
    private static final List<Integer> ALL_CARD_INDEXES = List.of(0, 1, 2, 3, 4, 5, 6, 7);

    @Test
    void playerWithNoCardsWinsWhenNextPlayerDeclinesToCallBluff()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        Cheat game = new Cheat(users);
        UUID lastPlayerId = currentPlayerId(game, users);
        UUID nextPlayerId = otherPlayerId(users, lastPlayerId);

        game.playCards(lastPlayerId, ALL_CARD_INDEXES, Rank.ACE);

        assertEquals(CheatGameStatus.IN_PROGRESS, game.getStatus());
        assertNull(game.getWinnerId());

        game.playCards(nextPlayerId, List.of(0), Rank.ACE);

        assertEquals(CheatGameStatus.FINISHED, game.getStatus());
        assertEquals(lastPlayerId, game.getWinnerId());
        assertEquals(8, game.getCardsForPlayer(nextPlayerId).size());
    }

    @Test
    void successfulBluffCallPreventsPlayerWithNoCardsFromWinning()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        Cheat game = new Cheat(users);
        UUID lastPlayerId = currentPlayerId(game, users);
        UUID nextPlayerId = otherPlayerId(users, lastPlayerId);
        Rank firstCardRank = game.getCardsForPlayer(lastPlayerId).get(0).getRank();
        Rank falseDeclaration = firstCardRank == Rank.ACE ? Rank.KING : Rank.ACE;

        game.playCards(lastPlayerId, ALL_CARD_INDEXES, falseDeclaration);
        game.callBluff(nextPlayerId);

        assertEquals(CheatGameStatus.IN_PROGRESS, game.getStatus());
        assertNull(game.getWinnerId());
        assertEquals(8, game.getCardsForPlayer(lastPlayerId).size());
    }

    @Test
    void unsuccessfulBluffCallConfirmsPlayerWithNoCardsAsWinner()
    {
        List<User> users = List.of(new User("Ada"), new User("Linus"));
        Cheat game = new Cheat(users);
        UUID lastPlayerId = currentPlayerId(game, users);
        UUID nextPlayerId = otherPlayerId(users, lastPlayerId);

        for (int cardNumber = 0; cardNumber < 7; cardNumber++)
        {
            playFirstCardTruthfully(game, lastPlayerId);
            playFirstCardTruthfully(game, nextPlayerId);
        }

        playFirstCardTruthfully(game, lastPlayerId);

        assertEquals(CheatGameStatus.IN_PROGRESS, game.getStatus());
        assertNull(game.getWinnerId());

        game.callBluff(nextPlayerId);

        assertEquals(CheatGameStatus.FINISHED, game.getStatus());
        assertEquals(lastPlayerId, game.getWinnerId());
    }

    private void playFirstCardTruthfully(Cheat game, UUID playerId)
    {
        Rank cardRank = game.getCardsForPlayer(playerId).get(0).getRank();
        game.playCards(playerId, List.of(0), cardRank);
    }

    private UUID currentPlayerId(Cheat game, List<User> users)
    {
        return users.stream()
            .map(User::getUniqueId)
            .filter(game::isPlayerTurn)
            .findFirst()
            .orElseThrow();
    }

    private UUID otherPlayerId(List<User> users, UUID playerId)
    {
        return users.stream()
            .map(User::getUniqueId)
            .filter(userId -> !userId.equals(playerId))
            .findFirst()
            .orElseThrow();
    }
}
