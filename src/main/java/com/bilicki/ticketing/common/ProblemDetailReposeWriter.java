package com.bilicki.ticketing.common;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

@Component
public class ProblemDetailReposeWriter {
    @Value("${app.error.base-uri}")
    private String errorBaseUri;

    private static final String CORRELATION_ID_KEY = "correlationId";

    private final ObjectMapper objectMapper;

    public ProblemDetailReposeWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String type, String title, String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(errorBaseUri + type));
        problemDetail.setTitle(title);
        problemDetail.setProperty(CORRELATION_ID_KEY, MDC.get(CORRELATION_ID_KEY));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }

}
