package com.bilicki.ticketing.user.web;

public record LoginResponse (
        String accessToken,
        long expiresIn
) {}
