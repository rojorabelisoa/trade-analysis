package com.srliq.app.paper;

import com.srliq.app.backtest.TradeStatistics;
import com.srliq.app.config.PaperProperties;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.web.ApiException;
import com.srliq.engine.backtest.Breakdown;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/paper")
public class PaperController {

    /**
     * Statistiques des trades simulés TERMINÉS (les trades OPEN sont comptés à part, leur R latent
     * n'entre ni dans le résumé ni dans la courbe).
     *
     * @param symbol      instrument, ou {@code null} = tous
     * @param timeframe   timeframe d'entrée, ou {@code null} = tous
     * @param openTrades  trades encore ouverts
     * @param pnlCurrency somme des résultats en devise des trades terminés
     * @param capital     capital fixe utilisé pour le dimensionnement
     * @param riskPercent risque par trade, en % du capital
     */
    public record PaperSummary(String symbol, String timeframe, int closedTrades, int openTrades, double pnlCurrency,
                               double capital, double riskPercent, TradeStatistics.SummaryView summary,
                               List<Breakdown.Row> bySession,
                               List<Breakdown.Row> byDirection,
                               List<Breakdown.Row> byOutcome,
                               List<TradeStatistics.EquityPoint> equityCurve) {
    }

    private final PaperTradeRepository trades;
    private final PaperTradingService service;
    private final InstrumentRepository instruments;
    private final PaperProperties paper;

    public PaperController(PaperTradeRepository trades, PaperTradingService service, InstrumentRepository instruments,
                           PaperProperties paper) {
        this.trades = trades;
        this.service = service;
        this.instruments = instruments;
        this.paper = paper;
    }

    /**
     * Trades simulés, du plus récent au plus ancien (heure du signal). {@code from} inclus,
     * {@code to} exclu (ISO 8601).
     */
    @GetMapping("/trades")
    public PaperTradeRepository.Page list(@RequestParam(required = false) String symbol,
                                          @RequestParam(required = false) String timeframe,
                                          @RequestParam(required = false) PaperTradeStatus status,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.badRequest("'from' doit précéder 'to'");
        }
        String sym = symbol == null ? null : instruments.require(symbol).symbol();
        return trades.search(new PaperTradeRepository.Filter(sym, timeframe, status, from, to), page, size);
    }

    @GetMapping("/summary")
    public PaperSummary summary(@RequestParam(required = false) String symbol,
                                @RequestParam(required = false) String timeframe) {
        String sym = symbol == null || symbol.isBlank() ? null : instruments.require(symbol).symbol();
        List<PaperTrade> closed = trades.findAll(
                new PaperTradeRepository.Filter(sym, timeframe, PaperTradeStatus.CLOSED, null, null));
        int open = trades.findAll(
                new PaperTradeRepository.Filter(sym, timeframe, PaperTradeStatus.OPEN, null, null)).size();
        List<Trade> engineTrades = closed.stream().map(PaperController::toEngine).toList();
        TradeStatistics.Stats stats = TradeStatistics.of(engineTrades);
        double pnl = closed.stream().mapToDouble(PaperTrade::pnlCurrency).sum();
        return new PaperSummary(sym, timeframe, closed.size(), open, pnl, paper.getCapital(), paper.getRiskPercent(),
                stats.summary(), stats.bySession(), stats.byDirection(), stats.byOutcome(), stats.equityCurve());
    }

    /** Relance le rejeu (tous les timeframes d'entrée stockés de l'instrument). */
    @PostMapping("/replay")
    public PaperTradingService.ReplayResult replay(@RequestParam String symbol) {
        if (!service.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Paper trading désactivé (srliq.paper.enabled=false)");
        }
        return service.replay(symbol);
    }

    /** Trade du moteur reconstruit depuis la base (pour les statistiques du moteur). */
    static Trade toEngine(PaperTrade t) {
        Signal s = new Signal(t.symbol(), t.direction(), t.signalTime(), t.entry(), t.stop(), t.target(), 0.0, null,
                null, Double.NaN, t.bias(), t.session(), null);
        return new Trade(s, t.entryTime(), t.entry(), t.exitTime(), t.exitPrice(), t.outcome(), t.r());
    }
}
