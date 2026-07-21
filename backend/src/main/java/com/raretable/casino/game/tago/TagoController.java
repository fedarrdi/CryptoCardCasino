package com.raretable.casino.game.tago;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tables/{tableId}/tago/users/{userId}")
public final class TagoController
{
    private final TagoGameService tagoGameService;

    public TagoController(TagoGameService tagoGameService)
    {
        this.tagoGameService = tagoGameService;
    }

    @GetMapping("/state")
    public TagoGameState getGameState(
        @PathVariable UUID tableId,
        @PathVariable UUID userId
    )
    {
        return tagoGameService.getGameState(tableId, userId);
    }

    @PostMapping("/actions/complete-betting-turn")
    public TagoGameState completeBettingTurn(
        @PathVariable UUID tableId,
        @PathVariable UUID userId
    )
    {
        return tagoGameService.completeBettingTurn(tableId, userId);
    }

    @PostMapping("/actions/fold")
    public TagoGameState fold(
        @PathVariable UUID tableId,
        @PathVariable UUID userId
    )
    {
        return tagoGameService.fold(tableId, userId);
    }

    @PostMapping("/actions/point-value")
    public TagoGameState choosePointValue(
        @PathVariable UUID tableId,
        @PathVariable UUID userId,
        @Valid @RequestBody ChoosePointValueRequest request
    )
    {
        return tagoGameService.choosePointValue(tableId, userId, request.value());
    }
}
