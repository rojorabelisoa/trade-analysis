package com.srliq.app.bias;

import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Biais fondamental. Les modifications ne relancent pas le calcul : appeler
 * {@code POST /api/signals/recompute} (les signaux déjà enregistrés ne sont jamais supprimés).
 */
@RestController
@RequestMapping("/api/bias")
public class BiasController {

    public record CreateBias(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 16) String symbol,
            @NotBlank @Pattern(regexp = "(?i)\\s*(long|short|neutral|off)\\s*",
                    message = "doit valoir long, short, neutral ou off") String bias,
            @Size(max = 500) String note) {
    }

    private final BiasRepository biases;
    private final InstrumentRepository instruments;

    public BiasController(BiasRepository biases, InstrumentRepository instruments) {
        this.biases = biases;
        this.instruments = instruments;
    }

    @GetMapping
    public List<BiasEntry> list(@RequestParam(required = false) String symbol) {
        return biases.findAll(symbol == null ? null : instruments.require(symbol).symbol());
    }

    @PostMapping
    public ResponseEntity<BiasEntry> create(@Valid @RequestBody CreateBias body) {
        Instrument ins = instruments.require(body.symbol());
        BiasEntry created = biases.insert(ins.id(), body.date(), body.bias().strip().toLowerCase(Locale.ROOT),
                body.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!biases.delete(id)) {
            throw ApiException.notFound("Biais " + id + " introuvable");
        }
        return ResponseEntity.noContent().build();
    }
}
