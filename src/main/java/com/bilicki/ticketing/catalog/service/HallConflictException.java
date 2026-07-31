package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class HallConflictException extends DomainException {
    public HallConflictException(String name) {
        super(
                HttpStatus.CONFLICT,
                "hall-conflict",
                "Hall Already Exists",
                "A hall with name '" + name + "' already exists in this venue."
        );
    }
}
