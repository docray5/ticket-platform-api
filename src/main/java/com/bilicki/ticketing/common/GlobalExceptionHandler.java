package com.bilicki.ticketing.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex, WebRequest request) {
        log.warn("Domain exception occurred: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problemDetail.setType(URI.create(ex.getType()));
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request payload");
        problemDetail.setType(URI.create("https://api.ticketing.dev/errors/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        problemDetail.setProperty("violations", errors);

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknownException(Exception ex) {
        log.error("Unhandled exception occurred", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support and quote the correlation ID.");
        problemDetail.setType(URI.create("https://api.ticketing.dev/errors/internal-server-error"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint violation occurred", ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A record with this unique information already exists.");
        problemDetail.setType(URI.create("https://api.ticketing.dev/errors/data-conflict"));
        problemDetail.setTitle("Data Conflict");
        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return problemDetail;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleAccessDeniedException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource."
        );
        problemDetail.setType(URI.create("access-denied"));
        problemDetail.setTitle("Access Denied");

        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        return problemDetail;
    }
}
