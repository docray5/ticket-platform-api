package com.bilicki.ticketing.common;

import org.springframework.http.HttpStatus;

public class ForbiddenActionException extends DomainException {
    public ForbiddenActionException(String detail) {
        super(
                HttpStatus.FORBIDDEN,
                "forbidden-action",
                "Forbidden Action",
                detail
        );
    }
}
