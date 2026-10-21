package com.umesh.decision.api;

import com.umesh.decision.api.dto.ErrorResponse;
import com.umesh.decision.engine.SimulationExecutionException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse validation(MethodArgumentNotValidException exception) {
        Map<String, String> details = new HashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        return new ErrorResponse(OffsetDateTime.now(), 400, "Validation failed", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse badRequest(IllegalArgumentException exception) {
        return new ErrorResponse(OffsetDateTime.now(), 400, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(SimulationExecutionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse simulationFailed(SimulationExecutionException exception) {
        log.warn("Monte Carlo simulation failed: {}", exception.getMessage(), exception);
        return new ErrorResponse(OffsetDateTime.now(), 503,
                "Simulation could not complete; already-completed progress was checkpointed and the request can be retried.",
                Map.of());
    }

    /**
     * Catch-all for anything not handled above. Without this, an unexpected failure (e.g. a
     * database outage, a serialization bug) fell through to Spring Boot's default error handling,
     * which returns a differently-shaped body than {@link ErrorResponse} and, depending on
     * configuration, can leak stack traces to the client. Every unhandled exception is now logged
     * server-side (with the stack trace) but only a generic message is returned to the caller.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ErrorResponse unexpected(Exception exception) {
        log.error("Unhandled exception while processing request", exception);
        return new ErrorResponse(OffsetDateTime.now(), 500, "An unexpected error occurred.", Map.of());
    }
}
