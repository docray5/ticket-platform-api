package com.bilicki.ticketing.user.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super(
                HttpStatus.UNAUTHORIZED,
                "https://api.ticketing.dev/errors/invalid-credentials",
                "Invalid Credentials",
                "The provided email or password is incorrect"
        );
    }
}
