package com.srliq.app.signal;

import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.web.ApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final SignalRepository signals;
    private final SignalComputationService computation;
    private final InstrumentRepository instruments;

    public SignalController(SignalRepository signals, SignalComputationService computation,
                            InstrumentRepository instruments) {
        this.signals = signals;
        this.computation = computation;
        this.instruments = instruments;
    }

    /**
     * Signaux du plus récent au plus ancien. {@code from} inclus, {@code to} exclu (ISO 8601,
     * ex. 2026-01-17T00:00:00Z), sur l'heure du signal.
     */
    @GetMapping
    public SignalRepository.Page list(@RequestParam(required = false) String symbol,
                                      @RequestParam(required = false) SignalSource source,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
                                      @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.badRequest("'from' doit précéder 'to'");
        }
        String sym = symbol == null ? null : instruments.require(symbol).symbol();
        return signals.search(new SignalRepository.Filter(sym, source, from, to), page, size);
    }

    /** Relance le moteur sur l'historique stocké de l'instrument ; seuls les nouveaux signaux sont ajoutés. */
    @PostMapping("/recompute")
    public RecomputeResult recompute(@RequestParam String symbol) {
        return computation.recompute(symbol);
    }
}
