package com.raretable.casino.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.raretable.casino.api.ApiError;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint
{
    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper)
    {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException
    {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(
            response.getOutputStream(),
            new ApiError("AUTHENTICATION_REQUIRED", "Authentication is required")
        );
    }
}
