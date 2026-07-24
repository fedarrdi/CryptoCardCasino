package com.raretable.casino.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.raretable.casino.table.TestUserFactory.createWalletUser;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

class TableServiceTests
{
    @Test
    void rejectsPlayerCountsOutsideEachGamesLimits()
    {
        UserService userService = new UserService();
        User creator = createWalletUser(userService, 1);
        TableService tableService = new TableService(userService);

        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.CHEAT, 1, creator.getUniqueId())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.CHEAT, 7, creator.getUniqueId())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.TAGO, 1, creator.getUniqueId())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.TAGO, 9, creator.getUniqueId())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.TIEN_LEN, 1, creator.getUniqueId())
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tableService.createTable(GameType.TIEN_LEN, 5, creator.getUniqueId())
        );
    }

    @Test
    void deletingCreatorRemovesWaitingTableAndItsPlayers()
    {
        UserService userService = new UserService();
        User creator = createWalletUser(userService, 1);
        User waitingPlayer = createWalletUser(userService, 2);
        TableService tableService = new TableService(userService);
        UUID tableId = tableService.createTable(GameType.CHEAT, 3, creator.getUniqueId());

        tableService.joinTable(tableId, waitingPlayer.getUniqueId());
        tableService.leaveTable(tableId, creator.getUniqueId());

        assertThrows(TableNotFoundException.class, () -> tableService.getTable(tableId));
    }

    @Test
    void nonCreatorLeavingKeepsCreatorAtWaitingTable()
    {
        UserService userService = new UserService();
        User creator = createWalletUser(userService, 1);
        User waitingPlayer = createWalletUser(userService, 2);
        TableService tableService = new TableService(userService);
        UUID tableId = tableService.createTable(GameType.CHEAT, 3, creator.getUniqueId());

        tableService.joinTable(tableId, waitingPlayer.getUniqueId());
        tableService.leaveTable(tableId, waitingPlayer.getUniqueId());

        GetTableResponse table = tableService.getAllTables().get(0);

        assertEquals(tableId, table.tableId());
        assertEquals(TableStatus.WAITING, table.status());
        assertEquals(1, table.playersJoined());
    }
}
