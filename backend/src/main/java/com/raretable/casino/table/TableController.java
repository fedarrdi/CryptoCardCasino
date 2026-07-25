package com.raretable.casino.table;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.security.WalletPrincipal;

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
        @Valid @RequestBody CreateTableRequest request,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        UUID tableId = tableService.createTable(
            request.gameType(),
            request.playersToStart(),
            principal.userId()
        );
        CreateTableResponse response = new CreateTableResponse(tableId);

        return ResponseEntity.created(URI.create("/api/tables/" + tableId)).body(response);
    }

    @PutMapping("/{tableId}/join")
    public ResponseEntity<Void> joinTable(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        tableService.joinTable(tableId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tableId}/leave")
    public ResponseEntity<Void> leaveTable(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        tableService.leaveTable(tableId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<GetTableResponse> getAllTables()
    {
        return tableService.getAllTables();
    }
}
