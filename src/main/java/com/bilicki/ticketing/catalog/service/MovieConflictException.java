package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class MovieConflictException extends DomainException {
    public MovieConflictException(String title) {
        super(
                HttpStatus.CONFLICT,
                "movie-conflict",
                "Movie Already Exists",
                "A movie with title '" + title + "' already exists."
        );
    }
}
