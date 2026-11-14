package com.srliq.app.signal;

import com.srliq.app.bias.BiasRepository;
import com.srliq.app.config.NewsProperties;
import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.marketdata.CandleRepository;
import com.srliq.app.marketdata.Timeframes;
import com.srliq.app.news.NewsRepository;
import com.srliq.engine.data.Resampler;
import com.srliq.engine.filter.BiasBook;
import com.srliq.engine.filter.NewsCalendar;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Signal;
import com.srliq.engine.strategy.SignalEngine;
import com.srliq.engine.strategy.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcule les signaux d'un instrument avec le moteur et enregistre les nouveaux (source ENGINE).
 *
 * <p>Pour chaque timeframe d'entrée stocké (5m, 15m…) : bougies LTF -> agrégation H1 (zones) et
 * H4 (tendance) par {@link Resampler} -> biais et annonces lus en base -> {@link SignalEngine#generate}.
 * Le moteur est rejoué sur tout l'historique stocké : le résultat est déterministe et identique au
 * backtest ; l'unicité en base rend le recalcul idempotent. Le moteur garantit l'absence de
 * look-ahead (pivots confirmés, bougies H1/H4 clôturées uniquement). Chaque signal est enregistré
 * dans sa propre transaction ({@link SignalRecorder}). Publie ensuite {@link SignalsRecomputedEvent}
 * (rejeu du paper trading).
 */
@Service
public class SignalComputationService {

    private static final Logger log = LoggerFactory.getLogger(SignalComputationService.class);

    private final InstrumentRepository instruments;
    private final CandleRepository candles;
    private final BiasRepository biases;
    private final NewsRepository news;
    private final SignalRecorder recorder;
    private final StrategyConfig config;
    private final NewsProperties newsProperties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public SignalComputationService(InstrumentRepository instruments, CandleRepository candles,
                                    BiasRepository biases, NewsRepository news, SignalRecorder recorder,
                                    StrategyConfig config, NewsProperties newsProperties, Clock clock,
                                    ApplicationEventPublisher events) {
        this.instruments = instruments;
        this.candles = candles;
        this.biases = biases;
        this.news = news;
        this.recorder = recorder;
        this.config = config;
        this.newsProperties = newsProperties;
        this.clock = clock;
        this.events = events;
    }

    /** Recalcule tous les timeframes d'entrée stockés pour {@code symbol}. */
    public RecomputeResult recompute(String symbol) {
        Instrument ins = instruments.require(symbol);
        List<RecomputeResult.TimeframeResult> out = new ArrayList<>();
        for (int seconds : candles.timeframes(ins.id())) {
            out.add(computeTimeframe(ins, Duration.ofSeconds(seconds)));
        }
        return new RecomputeResult(ins.symbol(), out);
    }

    /** Recalcule un seul timeframe d'entrée. */
    public RecomputeResult recompute(String symbol, Duration timeframe) {
        Instrument ins = instruments.require(symbol);
        return new RecomputeResult(ins.symbol(), List.of(computeTimeframe(ins, timeframe)));
    }

    private RecomputeResult.TimeframeResult computeTimeframe(Instrument ins, Duration tf) {
        String label = Timeframes.label(tf);
        if (!Timeframes.isEntryTimeframe(tf)) {
            return new RecomputeResult.TimeframeResult(label, 0, 0, 0,
                    "timeframe d'entrée non supporté (attendu < 1h et diviseur de l'heure)");
        }
        CandleSeries ltf = candles.load(ins.id(), tf);
        CandleSeries zones = Resampler.resample(ltf, Timeframes.ZONES);
        CandleSeries trend = Resampler.resample(ltf, Timeframes.TREND);
        if (ltf.size() < 2 || zones.size() < 2 || trend.size() < 2) {
            return new RecomputeResult.TimeframeResult(label, ltf.size(), 0, 0, "pas assez de bougies");
        }
        // Chaque instrument a son propre biais : sans aucune ligne pour lui, il est "neutral",
        // même si un biais existe pour un autre instrument. (La référence Python le mettrait "off".)
        var ownBias = biases.engineEntries().stream()
                .filter(e -> e.symbol().equalsIgnoreCase(ins.symbol()))
                .toList();
        BiasBook bias = ownBias.isEmpty() ? BiasBook.none(ins.symbol()) : BiasBook.of(ownBias, ins.symbol());
        NewsCalendar calendar = NewsCalendar.of(news.engineEvents(),
                Duration.ofMinutes(newsProperties.getBeforeMinutes()),
                Duration.ofMinutes(newsProperties.getAfterMinutes()));

        List<Signal> generated = SignalEngine.generate(zones, ltf, ins.symbol(), config, bias, calendar, trend);

        Instant now = clock.instant();
        int recorded = 0;
        for (Signal s : generated) {
            if (recorder.record(SignalRecord.fromEngine(s, ins.symbol(), label, now)).created()) {
                recorded++;
            }
        }
        log.info("Signaux {} {} : {} bougies, {} signaux, {} nouveaux", ins.symbol(), label, ltf.size(),
                generated.size(), recorded);
        // Paper trading : rejeu du backtest sur l'historique stocké (voir PaperTradingService).
        events.publishEvent(new SignalsRecomputedEvent(ins.symbol(), tf));
        return new RecomputeResult.TimeframeResult(label, ltf.size(), generated.size(), recorded, null);
    }
}
