package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import com.bilicki.ticketing.config.JwtAuthenticationFilter;
import com.bilicki.ticketing.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CatalogController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class})
)
@AutoConfigureMockMvc(addFilters = false)
public class CatalogControllerTest {
    @MockitoBean
    private CatalogService catalogService;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createVenue_ShouldReturn201Created() throws Exception {
        VenueRequest venueRequest = new VenueRequest("Cool Venue", "Some Street 123");
        VenueResponse venueResponse = new VenueResponse(UUID.randomUUID(), "Cool Venue", "Some Street 123");

        when(catalogService.createVenue(any(VenueRequest.class))).thenReturn(venueResponse);

        mockMvc.perform(post("/api/v1/admin/venues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(venueRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(venueResponse.id().toString()))
                .andExpect(jsonPath("$.name").value("Cool Venue"))
                .andExpect(jsonPath("$.address").value("Some Street 123"));
    }

    @Test
    void createVenue_ShouldReturn400BadRequest() throws Exception {
        VenueRequest venueRequest = new VenueRequest(null, "Some Street 123");

        mockMvc.perform(post("/api/v1/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(venueRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAllVenues_ShouldReturnList_And200Ok() throws Exception {
        VenueResponse response1 = new VenueResponse(UUID.randomUUID(), "Cool Venue", "Some Street 123");
        VenueResponse response2 = new VenueResponse(UUID.randomUUID(), "Another Venue", "Another Street 123");

        when(catalogService.getAllVenues()).thenReturn(List.of(response1, response2));

        mockMvc.perform(get("/api/v1/admin/venues").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].id").value(response1.id().toString()))
                .andExpect(jsonPath("$[0].name").value("Cool Venue"))
                .andExpect(jsonPath("$[0].address").value("Some Street 123"))
                .andExpect(jsonPath("$[1].id").value(response2.id().toString()))
                .andExpect(jsonPath("$[1].name").value("Another Venue"))
                .andExpect(jsonPath("$[1].address").value("Another Street 123"));
    }
}
