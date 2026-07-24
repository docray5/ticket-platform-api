package com.bilicki.ticketing.user.web;

import java.util.UUID;

public record RegisterResponse (
        UUID id,
        String email
) {}
