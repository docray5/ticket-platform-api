package com.bilicki.ticketing.user.service;

import com.bilicki.ticketing.user.internal.User;
import com.bilicki.ticketing.user.internal.UserRepository;
import com.bilicki.ticketing.user.web.LoginRequest;
import com.bilicki.ticketing.user.web.LoginResponse;
import com.bilicki.ticketing.user.web.RegisterRequest;
import com.bilicki.ticketing.user.web.RegisterResponse;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {
    private PasswordEncoder passwordEncoder;
    private UserRepository userRepository;
    private JwtService jwtService;

    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email()))
            throw new UserAlreadyExistsException(registerRequest.email());

        User user = new User();
        user.setEmail(registerRequest.email());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));

        user = userRepository.save(user);

        return new RegisterResponse(user.getId(), user.getEmail());
    }

    public LoginResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash()))
            throw new InvalidCredentialsException();

        return new LoginResponse(jwtService.generateToken(user), jwtService.getExpirationTime() / 1000);
    }
}
