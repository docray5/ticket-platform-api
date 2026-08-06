package com.bilicki.ticketing.catalog.web;

import com.bilicki.ticketing.catalog.service.CatalogService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1")
public class PublicCatalogController {
    private final CatalogService catalogService;

    @GetMapping("/movies")
    @ResponseStatus(HttpStatus.OK)
    public List<MovieResponse> getAllMovies(@PageableDefault(size = 20) Pageable pageable) {
        return catalogService.getAllMovies(pageable);
    }

    @GetMapping("/movies/{movieId}")
    @ResponseStatus(HttpStatus.OK)
    public MovieResponse getMovieById(@PathVariable UUID movieId) {
        return catalogService.getMovieById(movieId);
    }


    @GetMapping("/movies/{movieId}/showtimes")
    @ResponseStatus(HttpStatus.OK)
    public List<ShowtimeResponse> getAllShowtimesByMovieId(@PathVariable UUID movieId) {
        return catalogService.getAllShowtimesByMovieId(movieId);
    }
}
