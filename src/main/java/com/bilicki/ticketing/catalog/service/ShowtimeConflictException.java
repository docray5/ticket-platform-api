package com.bilicki.ticketing.catalog.service;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class ShowtimeConflictException extends DomainException {
  public ShowtimeConflictException() {
    super(
            HttpStatus.CONFLICT,
            "asd",
            "asd",
            "asd" // TODO this shit
    );
  }
}
