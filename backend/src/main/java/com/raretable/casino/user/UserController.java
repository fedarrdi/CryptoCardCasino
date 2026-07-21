package com.raretable.casino.user;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public final class UserController
{
    private final UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request)
    {
        User user = userService.login(request.name());
        return LoginResponse.from(user);
    }

    @GetMapping("/{userId}")
    public LoginResponse getUser(@PathVariable UUID userId)
    {
        User user = userService.getUser(userId);
        return LoginResponse.from(user);
    }
}
