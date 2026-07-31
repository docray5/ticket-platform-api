package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class MovieNotFoundException extends DomainException {
    public MovieNotFoundException() {
        super(
                HttpStatus.NOT_FOUND,
                "movie-not-found",
                "Movie Not Found",
                "The requested movie could not be found."
        );
    }
}
