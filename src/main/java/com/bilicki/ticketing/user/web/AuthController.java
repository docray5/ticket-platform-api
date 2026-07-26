package com.bilicki.ticketing.user.web;

import com.bilicki.ticketing.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class AuthController {

    private AuthService authService;

    @PostMapping(path = "/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse registerUser(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping(path = "/auth/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping(path = "/auth/me")
    public String getMyId(@AuthenticationPrincipal String userIdString) {
        UUID userId = UUID.fromString(userIdString);
        return "You are securely logged in! Your database UUID is: " + userId;
    }
}
