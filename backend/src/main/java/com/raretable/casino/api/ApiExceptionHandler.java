package com.raretable.casino.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.raretable.casino.auth.RateLimitExceededException;
import com.raretable.casino.auth.WalletAuthenticationException;
import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;
import com.raretable.casino.paper_trading.trading.InsufficientPaperMarginException;
import com.raretable.casino.paper_trading.trading.PaperOrderIdConflictException;
import com.raretable.casino.paper_trading.trading.PaperPositionNotFoundException;
import com.raretable.casino.paper_trading.trading.PaperPositionNotOpenException;
import com.raretable.casino.paper_trading.trading.PaperTradingSessionMismatchException;
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

    @ExceptionHandler(PaperPositionNotFoundException.class)
    public ResponseEntity<ApiError> handlePaperPositionNotFound(
        PaperPositionNotFoundException exception
    )
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("POSITION_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientPaperMarginException.class)
    public ResponseEntity<ApiError> handleInsufficientPaperMargin(
        InsufficientPaperMarginException exception
    )
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("INSUFFICIENT_MARGIN", exception.getMessage()));
    }

    @ExceptionHandler(PaperPositionNotOpenException.class)
    public ResponseEntity<ApiError> handlePaperPositionNotOpen(
        PaperPositionNotOpenException exception
    )
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("POSITION_NOT_OPEN", exception.getMessage()));
    }

    @ExceptionHandler(PaperOrderIdConflictException.class)
    public ResponseEntity<ApiError> handlePaperOrderIdConflict(
        PaperOrderIdConflictException exception
    )
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("ORDER_ID_REUSED", exception.getMessage()));
    }

    @ExceptionHandler(PaperTradingSessionMismatchException.class)
    public ResponseEntity<ApiError> handlePaperTradingSessionMismatch(
        PaperTradingSessionMismatchException exception
    )
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("SESSION_USER_MISMATCH", exception.getMessage()));
    }

    @ExceptionHandler(MarketDataSynchronizingException.class)
    public ResponseEntity<ApiError> handleMarketDataSynchronizing(
        MarketDataSynchronizingException exception
    )
    {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError("MARKET_DATA_SYNCHRONIZING", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IndexOutOfBoundsException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException exception)
    {
        return ResponseEntity.badRequest()
            .body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception
    )
    {
        return ResponseEntity.badRequest()
            .body(new ApiError(
                "INVALID_REQUEST",
                "Request parameter has an invalid type"
            ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
        HttpMessageNotReadableException exception
    )
    {
        return ResponseEntity.badRequest()
            .body(new ApiError("INVALID_REQUEST", "Request body is invalid"));
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidationFailure(
        HandlerMethodValidationException exception
    )
    {
        return ResponseEntity.badRequest()
            .body(new ApiError("VALIDATION_FAILED", "Request validation failed"));
    }
}
