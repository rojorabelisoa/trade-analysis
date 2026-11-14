package com.srliq.app.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.srliq.app.bias.BiasRepository;
import com.srliq.app.config.NewsProperties;
import com.srliq.app.config.StrategyProperties;
import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.marketdata.Timeframes;
import com.srliq.app.news.NewsRepository;
import com.srliq.app.web.ApiException;
import com.srliq.engine.backtest.Backtester;
import com.srliq.engine.backtest.Breakdown;
import com.srliq.engine.backtest.IntradayRules;
import com.srliq.engine.data.Resampler;
import com.srliq.engine.data.TradingViewCsv;
import com.srliq.engine.filter.BiasBook;
import com.srliq.engine.filter.NewsCalendar;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import com.srliq.engine.strategy.SignalEngine;
import com.srliq.engine.strategy.StrategyConfig;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Backtest à la demande sur un export CSV TradingView : signaux du moteur + {@link Backtester} +
 * statistiques, entièrement en mémoire (rien n'est écrit en base ; biais et annonces peuvent être
 * lus en base). Même chaîne de calcul que l'application : bougies -> H1 (zones) / H4 (tendance)
 * -> {@link SignalEngine} -> {@link Backtester}.
 *
 * <p>Le moteur n'expose pas d'entonnoir de diagnostic (compteurs d'étapes du Pine) : la réponse
 * n'en contient pas.
 */
@Service
public class OnDemandBacktestService {

    /** Paramètres d'un backtest. {@code strategyOverrides} : JSON des clés de {@code srliq.strategy} (camelCase). */
    public record Request(String symbol, byte[] csv, double cost, boolean intraday, IntradayRules intradayRules,
                          int maxHoldBars, boolean useBias, boolean useNews, String strategyOverrides) {
    }

    public record Parameters(double cost, boolean intraday, IntradayRules intradayRules, int maxHoldBars,
                             boolean useBias, boolean useNews, StrategyConfig strategy) {
    }

    /** Trade du backtest ; {@code openAtEnd} : sorti en timeout sur la dernière bougie, durée max non atteinte. */
    public record TradeView(Instant signalTime, Direction direction, Instant entryTime, double entry, double stop,
                            double target, Instant exitTime, double exit, Outcome outcome, double r, String session,
                            String bias, String zone, boolean openAtEnd) {
    }

    public record SignalView(Instant time, Direction direction, double entry, double stop, double target, double rr,
                             String zone, Instant sweepTime, Double sweepExtreme, String bias, String session,
                             Double nextZone) {
    }

    public record Result(String symbol, String timeframe, int candles, Instant firstCandle, Instant lastCandle,
                         Parameters parameters, List<SignalView> signals, List<TradeView> trades,
                         TradeStatistics.SummaryView summary, List<Breakdown.Row> bySession,
                         List<Breakdown.Row> byDirection, List<Breakdown.Row> byOutcome,
                         List<TradeStatistics.EquityPoint> equityCurve) {
    }

    private final InstrumentRepository instruments;
    private final BiasRepository biases;
    private final NewsRepository news;
    private final NewsProperties newsProperties;
    private final StrategyConfig config;
    private final ObjectMapper overridesMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    public OnDemandBacktestService(InstrumentRepository instruments, BiasRepository biases,
                                   NewsRepository news, NewsProperties newsProperties,
                                   StrategyConfig config) {
        this.instruments = instruments;
        this.biases = biases;
        this.news = news;
        this.newsProperties = newsProperties;
        this.config = config;
    }

    public Result run(Request req) {
        Instrument ins = instruments.require(req.symbol());
        if (req.cost() < 0 || !Double.isFinite(req.cost())) {
            throw ApiException.badRequest("Le coût doit être >= 0");
        }
        if (req.maxHoldBars() < 1) {
            throw ApiException.badRequest("maxHoldBars doit être >= 1");
        }
        StrategyConfig cfg = strategy(req.strategyOverrides());

        String text = new String(req.csv(), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        CandleSeries ltf = TradingViewCsv.parse(text);
        if (ltf.size() < 2) {
            throw ApiException.badRequest("Au moins deux bougies sont nécessaires pour déduire le timeframe");
        }
        Duration tf = ltf.barDuration();
        if (!Timeframes.isEntryTimeframe(tf)) {
            throw ApiException.badRequest("Timeframe " + Timeframes.label(tf)
                    + " non supporté (attendu < 1h et diviseur de l'heure, ex. 5m, 15m)");
        }
        CandleSeries zones = Resampler.resample(ltf, Timeframes.ZONES);
        CandleSeries trend = Resampler.resample(ltf, Timeframes.TREND);
        if (zones.size() < 2 || trend.size() < 2) {
            throw ApiException.badRequest("Pas assez de bougies pour agréger en H1 et H4");
        }
        BiasBook bias = BiasBook.none(ins.symbol());
        if (req.useBias()) {
            var own = biases.engineEntries().stream().filter(e -> e.symbol().equalsIgnoreCase(ins.symbol())).toList();
            if (!own.isEmpty()) {
                bias = BiasBook.of(own, ins.symbol());
            }
        }
        NewsCalendar calendar = req.useNews()
                ? NewsCalendar.of(news.engineEvents(), Duration.ofMinutes(newsProperties.getBeforeMinutes()),
                Duration.ofMinutes(newsProperties.getAfterMinutes()))
                : NewsCalendar.none();

        List<Signal> signals = SignalEngine.generate(zones, ltf, ins.symbol(), cfg, bias, calendar, trend);
        IntradayRules rules = req.intraday() ? req.intradayRules() : null;
        List<Trade> trades = Backtester.run(ltf, signals, req.cost(), req.maxHoldBars(), rules);
        TradeStatistics.Stats stats = TradeStatistics.of(trades);

        long lastMs = ltf.timeMillis(ltf.size() - 1);
        List<TradeView> tradeViews = new ArrayList<>(trades.size());
        for (Trade t : trades) {
            boolean openAtEnd = t.outcome() == Outcome.TIMEOUT && t.exitTime().toEpochMilli() == lastMs
                    && barsBetween(ltf, t.entryTime(), t.exitTime()) < req.maxHoldBars();
            Signal s = t.signal();
            tradeViews.add(new TradeView(s.time(), s.direction(), t.entryTime(), t.entry(), s.stop(), s.target(),
                    t.exitTime(), t.exit(), t.outcome(), t.r(), s.session(), s.bias(), s.zone(), openAtEnd));
        }
        List<SignalView> signalViews = signals.stream()
                .map(s -> new SignalView(s.time(), s.direction(), s.entry(), s.stop(), s.target(), s.rr(), s.zone(),
                        s.sweepTime(), Double.isFinite(s.sweepExtreme()) ? s.sweepExtreme() : null, s.bias(),
                        s.session(), s.nextZone()))
                .toList();
        return new Result(ins.symbol(), Timeframes.label(tf), ltf.size(), ltf.time(0), ltf.time(ltf.size() - 1),
                new Parameters(req.cost(), rules != null, rules, req.maxHoldBars(), req.useBias(), req.useNews(), cfg),
                signalViews, tradeViews, stats.summary(), stats.bySession(), stats.byDirection(), stats.byOutcome(),
                stats.equityCurve());
    }

    /** Configuration de l'application, avec les surcharges JSON éventuelles (clés de {@code srliq.strategy}). */
    StrategyConfig strategy(String overrides) {
        if (overrides == null || overrides.isBlank()) {
            return config;
        }
        StrategyProperties props = StrategyProperties.from(config);
        try {
            overridesMapper.readerForUpdating(props).readValue(overrides);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Surcharges de stratégie invalides : " + e.getOriginalMessage());
        }
        return props.toConfig();
    }

    private static int barsBetween(CandleSeries s, Instant from, Instant to) {
        long a = from.toEpochMilli();
        long b = to.toEpochMilli();
        int count = 0;
        for (int i = 0; i < s.size(); i++) {
            long t = s.timeMillis(i);
            if (t > a && t <= b) {
                count++;
            }
        }
        return count;
    }
}
