package com.bilicki.ticketing.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    @Getter
    private final HttpStatus status;
    @Getter
    private final String type;
    @Getter
    private final String title;

    public DomainException(HttpStatus status, String type, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
    }
}
