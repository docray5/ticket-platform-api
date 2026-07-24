package com.bilicki.ticketing.user.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends DomainException {
    public UserAlreadyExistsException(String email) {
        super(
                HttpStatus.CONFLICT,
                "https://api.ticketing.dev/errors/email-taken",
                "Email already registered",
                "The email " + email + " is already in use.");
    }
}
