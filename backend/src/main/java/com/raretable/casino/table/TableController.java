package com.raretable.casino.table;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.user.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tables")
public final class TableController
{
    private final TableService tableService;

    public TableController(TableService tableService)
    {
        this.tableService = tableService;
    }

    @PostMapping
    public ResponseEntity<CreateTableResponse> createTable(
        @Valid @RequestBody CreateTableRequest request
    )
    {
        UUID tableId = tableService.createTable(request.gameType(), request.playersToStart());
        CreateTableResponse response = new CreateTableResponse(tableId);

        return ResponseEntity.created(URI.create("/api/tables/" + tableId)).body(response);
    }

    @PostMapping("/{tableId}/join")
    public JoinTableResponse joinTable(
        @PathVariable UUID tableId,
        @Valid @RequestBody JoinTableRequest request
    )
    {
        User user = tableService.joinTable(tableId, request.name());
        return new JoinTableResponse(user.getUniqueId(), user.getName());
    }
}
