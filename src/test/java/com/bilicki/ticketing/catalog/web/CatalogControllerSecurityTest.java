package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import com.bilicki.ticketing.common.ProblemDetailReposeWriter;
import com.bilicki.ticketing.config.JwtAuthenticationFilter;
import com.bilicki.ticketing.config.RestAccessDeniedHandler;
import com.bilicki.ticketing.config.RestAuthenticationEntryPoint;
import com.bilicki.ticketing.config.SecurityConfig;
import com.bilicki.ticketing.user.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAccessDeniedHandler.class,
        RestAuthenticationEntryPoint.class,
        ProblemDetailReposeWriter.class
})
public class CatalogControllerSecurityTest {
    private final String errorBaseUri = "https://api.ticketing.dev/errors/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private JwtService jwtService;

    private static final String VENUE_BODY = """
            {"name": "Cool Venue", "address": "Some Street 123"}
            """;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateVenue() throws Exception {
        mockMvc.perform(post("/api/v1/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENUE_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENUE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(errorBaseUri + "access-denied"))
                .andExpect(jsonPath("$.status").value(403));

        verify(catalogService, never()).createVenue(any());
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VENUE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(errorBaseUri + "unauthorized"));

        verify(catalogService, never()).createVenue(any());
    }
}
