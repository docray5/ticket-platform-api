package com.bilicki.ticketing.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Clock clock;
    private final ProblemDetailReposeWriter problemDetailReposeWriter;

    private final Long expiryHours;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            problemDetailReposeWriter.write(
                    response,
                    HttpStatus.BAD_REQUEST,
                    "missing-idempotency-key",
                    "Missing Header",
                    "Idempotency-Key header is required."
            );
            return;
        }

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());

        String endpoint = request.getRequestURI();

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = hashBody(cachedRequest.getCachedBody());

        IdempotencyKey idempotencyKey = new IdempotencyKey(key, userId, endpoint, requestHash, Instant.now(clock).plus(expiryHours, ChronoUnit.HOURS));

        try {
            idempotencyKeyRepository.saveAndFlush(idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey existingKey = idempotencyKeyRepository.findByKeyAndUserIdAndEndpoint(key, userId, endpoint).orElseThrow();

            if (!existingKey.getRequestHash().equals(requestHash)) {
                problemDetailReposeWriter.write(
                        response,
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "idempotency-conflict",
                        "Idempotency Conflict",
                        "Same key used with a different request body."
                );
                return;
            }

            if (existingKey.getStatus() == IdempotencyKey.IdempotencyStatus.PENDING) {
                problemDetailReposeWriter.write(
                        response,
                        HttpStatus.CONFLICT,
                        "concurrent-request",
                        "Concurrent Request Processing",
                        "A request with this key is currently processing. Please wait."
                );
                return;
            }

            response.setStatus(existingKey.getResponseStatus());
            if (existingKey.getResponseStatus() >= 400) {
                response.setContentType("application/problem+json");
            } else {
                response.setContentType("application/json");
            }
            response.getWriter().write(existingKey.getResponseBody());

            return;
        }

        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);

            String responseBody = new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
            idempotencyKey.setStatus(IdempotencyKey.IdempotencyStatus.COMPLETED);
            idempotencyKey.setResponseBody(responseBody);
            idempotencyKey.setResponseStatus(cachedResponse.getStatus());
            idempotencyKeyRepository.save(idempotencyKey);

        } catch (Exception e) {
            idempotencyKeyRepository.delete(idempotencyKey);
            throw e;
        } finally {
            cachedResponse.copyBodyToResponse();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isHold = pathMatcher.match("/api/v1/showtimes/*/holds", path) && method.equals("POST");
        boolean isBooking = pathMatcher.match("/api/v1/bookings", path) && method.equals("POST");

        return !(isHold || isBooking);
    }

    private String hashBody(byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
