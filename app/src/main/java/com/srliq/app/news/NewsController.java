package com.srliq.app.news;

import com.srliq.app.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Annonces économiques. Les modifications ne relancent pas le calcul : appeler
 * {@code POST /api/signals/recompute}.
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    /** {@code time} en ISO 8601 avec fuseau ("2026-01-13T13:30:00Z"). */
    public record CreateNews(
            @NotNull Instant time,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "doit être un code devise ISO à 3 lettres")
            String currency,
            @NotBlank @Pattern(regexp = "(?i)\\s*(low|medium|high)\\s*", message = "doit valoir low, medium ou high")
            String impact,
            @NotBlank @Size(max = 200) String event) {
    }

    private final NewsRepository news;

    public NewsController(NewsRepository news) {
        this.news = news;
    }

    @GetMapping
    public List<NewsEvent> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return news.find(from, to);
    }

    @PostMapping
    public ResponseEntity<NewsEvent> create(@Valid @RequestBody CreateNews body) {
        NewsEvent created = news.insert(body.time(), body.currency().toUpperCase(Locale.ROOT),
                body.impact().strip().toLowerCase(Locale.ROOT), body.event().strip());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!news.delete(id)) {
            throw ApiException.notFound("Annonce " + id + " introuvable");
        }
        return ResponseEntity.noContent().build();
    }
}
