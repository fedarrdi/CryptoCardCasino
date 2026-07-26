package com.raretable.casino.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.raretable.casino.table.TestUserFactory.createWalletUser;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.raretable.casino.InfrastructureTestConfiguration;
import com.raretable.casino.game.GameType;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Import(InfrastructureTestConfiguration.class)
class TableServiceTests
{
    @Autowired
    private UserService userService;

    @Test
    void rejectsPlayerCountsOutsideCheatLimits()
    {
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
    }

    @Test
    void deletingCreatorRemovesWaitingTableAndItsPlayers()
    {
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
