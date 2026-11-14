package com.srliq.app.trading;

import com.srliq.app.broker.BrokerAdapter;
import com.srliq.app.broker.LiveTradingGuard;
import com.srliq.app.config.PaperProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Coupe-circuit : {@code POST /api/trading/halt}, {@code POST /api/trading/resume}, {@code GET /api/trading/status}. */
@RestController
@RequestMapping("/api/trading")
public class TradingController {

    /** Corps de {@code POST /api/trading/halt}. */
    public record HaltRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /**
     * État du coupe-circuit et du mode d'exécution.
     *
     * @param halted             coupe-circuit actif
     * @param since              début de l'arrêt en cours
     * @param reason             motif de l'arrêt en cours
     * @param paperTrading       paper trading activé
     * @param liveTradingEnabled toujours {@code false} (l'application refuse de démarrer sinon)
     * @param broker             adaptateur d'exécution ("paper")
     * @param history            dernières périodes d'arrêt, la plus récente d'abord
     */
    public record Status(boolean halted, Instant since, String reason, boolean paperTrading,
                         boolean liveTradingEnabled, String broker, List<TradingHalt> history) {
    }

    private final TradingHaltService halts;
    private final PaperProperties paper;
    private final LiveTradingGuard live;
    private final BrokerAdapter broker;

    public TradingController(TradingHaltService halts, PaperProperties paper, LiveTradingGuard live,
                             BrokerAdapter broker) {
        this.halts = halts;
        this.paper = paper;
        this.live = live;
        this.broker = broker;
    }

    @PostMapping("/halt")
    public Status halt(@Valid @RequestBody HaltRequest request) {
        halts.halt(request.reason());
        return status();
    }

    @PostMapping("/resume")
    public Status resume() {
        halts.resume();
        return status();
    }

    @GetMapping("/status")
    public Status status() {
        var active = halts.active();
        return new Status(active.isPresent(), active.map(TradingHalt::startedAt).orElse(null),
                active.map(TradingHalt::reason).orElse(null), paper.isEnabled(), live.liveEnabled(), broker.id(),
                halts.recent(20));
    }
}
