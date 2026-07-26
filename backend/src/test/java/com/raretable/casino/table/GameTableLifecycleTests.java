package com.raretable.casino.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.raretable.casino.table.TestUserFactory.createWalletUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.raretable.casino.InfrastructureTestConfiguration;
import com.raretable.casino.common.Rank;
import com.raretable.casino.game.GameType;
import com.raretable.casino.game.cheat.Cheat;
import com.raretable.casino.game.cheat.CheatGameService;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Import(InfrastructureTestConfiguration.class)
class GameTableLifecycleTests
{
    @Autowired
    private UserService userService;

    @Test
    void closesCheatTableAfterWinnerIsConfirmed()
    {
        GameSetup setup = createStartedCheatGame();
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
    void deletesOnlyClosedTablesThatReachedTheRetentionCutoff()
    {
        GameSetup setup = createStartedCheatGame();
        Table table = setup.tableService().getTable(setup.tableId());

        setup.tableService().leaveTable(
            setup.tableId(),
            setup.users().get(0).getUniqueId()
        );

        Instant closedAt = table.getClosedAt();
        setup.tableService().deleteClosedTablesBefore(closedAt.minusNanos(1));

        assertEquals(table, setup.tableService().getTable(setup.tableId()));

        setup.tableService().deleteClosedTablesBefore(closedAt);

        assertThrows(
            TableNotFoundException.class,
            () -> setup.tableService().getTable(setup.tableId())
        );
    }

    @Test
    void leavingTwoPlayerCheatAwardsRemainingPlayerAndClosesTable()
    {
        GameSetup setup = createStartedCheatGame();
        UUID leavingPlayerId = setup.users().get(0).getUniqueId();
        UUID remainingPlayerId = setup.users().get(1).getUniqueId();

        setup.tableService().leaveTable(setup.tableId(), leavingPlayerId);

        Table table = setup.tableService().getTable(setup.tableId());

        assertEquals(TableStatus.CLOSED, table.getStatus());
        assertEquals(List.of(setup.users().get(1)), table.getUsers());
        assertEquals(
            remainingPlayerId,
            ((Cheat) table.getGame()).getWinnerId()
        );
        assertThrows(
            IllegalStateException.class,
            () -> setup.tableService().joinTable(setup.tableId(), leavingPlayerId)
        );
    }

    @Test
    void forfeitedSeatCannotBeRejoinedOrReplaced()
    {
        User creator = createWalletUser(userService, 1);
        User secondPlayer = createWalletUser(userService, 2);
        User thirdPlayer = createWalletUser(userService, 3);
        User replacement = createWalletUser(userService, 4);
        TableService tableService = new TableService(userService);
        UUID tableId = tableService.createTable(GameType.CHEAT, 3, creator.getUniqueId());

        tableService.joinTable(tableId, secondPlayer.getUniqueId());
        tableService.joinTable(tableId, thirdPlayer.getUniqueId());
        tableService.leaveTable(tableId, creator.getUniqueId());

        Table table = tableService.getTable(tableId);

        assertEquals(TableStatus.IN_GAME, table.getStatus());
        assertEquals(2, table.getUsers().size());
        assertFalse(table.getGame().isFinished());
        assertThrows(
            IllegalStateException.class,
            () -> tableService.joinTable(tableId, creator.getUniqueId())
        );
        assertThrows(
            IllegalStateException.class,
            () -> tableService.joinTable(tableId, replacement.getUniqueId())
        );
    }

    private GameSetup createStartedCheatGame()
    {
        User creator = createWalletUser(userService, 1);
        User opponent = createWalletUser(userService, 2);
        TableService tableService = new TableService(userService);
        UUID tableId = tableService.createTable(
            GameType.CHEAT,
            2,
            creator.getUniqueId()
        );

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
