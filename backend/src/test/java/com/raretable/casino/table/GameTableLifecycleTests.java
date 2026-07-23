package com.raretable.casino.table;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.common.Rank;
import com.raretable.casino.game.GameType;
import com.raretable.casino.game.cheat.Cheat;
import com.raretable.casino.game.cheat.CheatGameService;
import com.raretable.casino.game.tago.Tago;
import com.raretable.casino.game.tago.TagoGameService;
import com.raretable.casino.game.tienlen.TienLenGameService;
import com.raretable.casino.game.tienlen.TienLenGameState;
import com.raretable.casino.game.tienlen.TienLenCombinationType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

class GameTableLifecycleTests
{
    @Test
    void closesCheatTableAfterWinnerIsConfirmed()
    {
        GameSetup setup = createStartedGame(GameType.CHEAT);
        Cheat game = (Cheat) setup.tableService().getTable(setup.tableId()).getGame();
        CheatGameService gameService = new CheatGameService(setup.tableService());
        UUID finishingPlayerId = currentPlayerId(game, setup.users());
        UUID nextPlayerId = otherPlayerId(setup.users(), finishingPlayerId);

        gameService.playCards(
            setup.tableId(),
            finishingPlayerId,
            List.of(0, 1, 2, 3, 4, 5, 6, 7),
            Rank.ACE
        );

        assertEquals(TableStatus.IN_GAME, setup.tableService().getTable(setup.tableId()).getStatus());

        gameService.playCards(setup.tableId(), nextPlayerId, List.of(0), Rank.ACE);

        assertEquals(TableStatus.CLOSED, setup.tableService().getTable(setup.tableId()).getStatus());
    }

    @Test
    void closesTagoTableAfterGameFinishes()
    {
        GameSetup setup = createStartedGame(GameType.TAGO);
        Tago game = (Tago) setup.tableService().getTable(setup.tableId()).getGame();
        TagoGameService gameService = new TagoGameService(setup.tableService());

        gameService.fold(setup.tableId(), game.getCurrentPlayerId());

        assertEquals(TableStatus.CLOSED, setup.tableService().getTable(setup.tableId()).getStatus());
    }

    @Test
    void closesTienLenTableAfterPlayerUsesLastCard()
    {
        GameSetup setup = createStartedGame(GameType.TIEN_LEN);
        TienLenGameService gameService = new TienLenGameService(setup.tableService());
        UUID leaderId = gameService
            .getGameState(setup.tableId(), setup.users().get(0).getUniqueId())
            .currentPlayerId();

        for (int cardsPlayed = 0; cardsPlayed < 13; cardsPlayed++)
        {
            TienLenGameState state = gameService.playCards(
                setup.tableId(),
                leaderId,
                List.of(0),
                TienLenCombinationType.SINGLE
            );

            if (cardsPlayed < 12)
            {
                gameService.pass(setup.tableId(), state.currentPlayerId());
            }
        }

        assertEquals(TableStatus.CLOSED, setup.tableService().getTable(setup.tableId()).getStatus());
    }

    private GameSetup createStartedGame(GameType gameType)
    {
        UserService userService = new UserService();
        User creator = userService.login("Creator");
        User opponent = userService.login("Opponent");
        TableService tableService = new TableService(userService);
        UUID tableId = tableService.createTable(gameType, 2, creator.getUniqueId());

        tableService.joinTable(tableId, opponent.getUniqueId());

        return new GameSetup(tableService, tableId, List.of(creator, opponent));
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

    private record GameSetup(
        TableService tableService,
        UUID tableId,
        List<User> users
    )
    {
    }
}
