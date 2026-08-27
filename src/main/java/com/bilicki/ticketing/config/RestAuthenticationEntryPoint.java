package com.bilicki.ticketing.config;

import com.bilicki.ticketing.common.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailResponseWriter responseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        responseWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication Required",
                "A valid Bearer token is required to access this resource."
        );
    }
}
