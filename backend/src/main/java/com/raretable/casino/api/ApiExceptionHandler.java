package com.raretable.casino.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.raretable.casino.auth.RateLimitExceededException;
import com.raretable.casino.auth.WalletAuthenticationException;
import com.raretable.casino.table.TableNotFoundException;
import com.raretable.casino.user.UserNotFoundException;

@RestControllerAdvice
public final class ApiExceptionHandler
{
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(
        RateLimitExceededException exception
    )
    {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(
                HttpHeaders.RETRY_AFTER,
                Long.toString(exception.retryAfterSeconds())
            )
            .body(new ApiError("RATE_LIMIT_EXCEEDED", exception.getMessage()));
    }

    @ExceptionHandler(WalletAuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailure(
        WalletAuthenticationException exception
    )
    {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiError("AUTHENTICATION_FAILED", exception.getMessage()));
    }

    @ExceptionHandler({TableNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IndexOutOfBoundsException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException exception)
    {
        return ResponseEntity.badRequest()
            .body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException exception)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("INVALID_GAME_STATE", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(MethodArgumentNotValidException exception)
    {
        return ResponseEntity.badRequest()
            .body(new ApiError("VALIDATION_FAILED", "Request validation failed"));
    }
}
