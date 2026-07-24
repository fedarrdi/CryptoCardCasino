package com.raretable.casino.game.tago;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.security.WalletPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tables/{tableId}/tago")
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
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tagoGameService.getGameState(tableId, principal.userId());
    }

    @PostMapping("/actions/complete-betting-turn")
    public TagoGameState completeBettingTurn(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tagoGameService.completeBettingTurn(tableId, principal.userId());
    }

    @PostMapping("/actions/fold")
    public TagoGameState fold(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tagoGameService.fold(tableId, principal.userId());
    }

    @PostMapping("/actions/point-value")
    public TagoGameState choosePointValue(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal,
        @Valid @RequestBody ChoosePointValueRequest request
    )
    {
        return tagoGameService.choosePointValue(
            tableId,
            principal.userId(),
            request.value()
        );
    }
}
