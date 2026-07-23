package com.raretable.casino.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

class TableServiceTests
{
    @Test
    void deletingCreatorRemovesWaitingTableAndItsPlayers()
    {
        UserService userService = new UserService();
        User creator = userService.login("Creator");
        User waitingPlayer = userService.login("Waiting player");
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
        User creator = userService.login("Creator");
        User waitingPlayer = userService.login("Waiting player");
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
