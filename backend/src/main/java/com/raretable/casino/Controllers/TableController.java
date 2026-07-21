package com.raretable.casino.Controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.Services.TableService;
import com.raretable.casino.Users.User;

@RestController
@RequestMapping("/api/tables")
public class TableController
{
    private final TableService tableService;

    public TableController(TableService tableService)
    {
        this.tableService = tableService;
    }

    @PostMapping
    public UUID create_table(@RequestParam int players_to_start)
    {
        return tableService.create_table(players_to_start);
    }

    @PostMapping("/{tableId}/join")
    public User join_table(@PathVariable UUID tableId, @RequestBody User user)
    {
        return tableService.join_table(tableId, user);
    }
}
