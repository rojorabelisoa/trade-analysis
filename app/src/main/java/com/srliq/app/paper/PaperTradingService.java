package com.srliq.app.paper;

import com.srliq.app.config.IntradayProperties;
import com.srliq.app.config.PaperProperties;
import com.srliq.app.instrument.Instrument;
import com.srliq.app.instrument.InstrumentRepository;
import com.srliq.app.marketdata.CandleRepository;
import com.srliq.app.marketdata.Timeframes;
import com.srliq.app.paper.PaperTradeRepository.Values;
import com.srliq.app.signal.SignalRecord;
import com.srliq.app.signal.SignalRepository;
import com.srliq.app.signal.SignalsRecomputedEvent;
import com.srliq.app.trading.TradingHalt;
import com.srliq.app.trading.TradingHaltRepository;
import com.srliq.app.trading.TradingHaltService;
import com.srliq.engine.backtest.Backtester;
import com.srliq.engine.backtest.IntradayRules;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Outcome;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paper trading = <b>rejeu déterministe du backtest du moteur</b>, pas une seconde implémentation
 * des règles.
 *
 * <p>Après chaque calcul des signaux d'un instrument sur un timeframe d'entrée
 * ({@link SignalsRecomputedEvent}, donc après chaque ingestion de bougies), le service relance
 * {@link Backtester#run} sur toutes les bougies stockées de ce timeframe et sur les signaux
 * <b>ENGINE</b> de l'instrument et de ce timeframe, avec le coût, la durée maximale et les règles
 * intraday configurés, puis synchronise la table {@code paper_trade} (clé : instrument, timeframe,
 * heure et sens du signal). Le paper trading et le backtest ne peuvent donc pas diverger.
 *
 * <ul>
 *   <li><b>Trade encore ouvert</b> : un trade que le moteur clôt en {@code timeout} sur la toute
 *       dernière bougie disponible, alors que la durée maximale n'est pas atteinte, n'est pas
 *       terminé (il manque des données) : il est enregistré OPEN (prix courant = dernière clôture,
 *       R latent) et recalculé au rejeu suivant.</li>
 *   <li><b>Signaux TRADINGVIEW</b> : sans bougies, pas de prix, donc pas de simulation. Ils ne
 *       donnent lieu à aucun trade simulé.</li>
 *   <li><b>Coupe-circuit</b> : les signaux dont l'heure tombe dans une période d'arrêt sont retirés
 *       avant le rejeu (aucune nouvelle entrée) ; un trade ouvert avant l'arrêt est géré jusqu'à sa
 *       sortie.</li>
 *   <li><b>Dimensionnement</b> : {@link PositionSizer}, capital fixe.</li>
 * </ul>
 *
 * <p>Un trade stocké que le rejeu ne produit plus (par exemple après l'ajout d'une période d'arrêt
 * ou de signaux) est supprimé : la table reflète toujours le dernier rejeu.
 */
@Service
public class PaperTradingService {

    private static final Logger log = LoggerFactory.getLogger(PaperTradingService.class);

    /** Résultat d'un rejeu, par timeframe. */
    public record ReplayResult(String symbol, List<TimeframeReplay> timeframes) {
    }

    /**
     * @param signals        signaux ENGINE du timeframe
     * @param excludedByHalt signaux retirés car émis pendant un arrêt du coupe-circuit
     * @param trades         trades produits par le moteur
     * @param open           dont trades encore ouverts
     * @param skipped        raison si le rejeu n'a pas été fait, sinon {@code null}
     */
    public record TimeframeReplay(String timeframe, int candles, int signals, int excludedByHalt, int trades,
                                  int open, int inserted, int updated, int deleted, int unchanged, String skipped) {
    }

    private final InstrumentRepository instruments;
    private final CandleRepository candles;
    private final SignalRepository signals;
    private final TradingHaltRepository halts;
    private final PaperTradeRepository trades;
    private final PaperProperties paper;
    private final IntradayRules intraday;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaperTradingService(InstrumentRepository instruments, CandleRepository candles, SignalRepository signals,
                               TradingHaltRepository halts, PaperTradeRepository trades, PaperProperties paper,
                               IntradayProperties intraday, ApplicationEventPublisher events,
                               PlatformTransactionManager txManager, Clock clock) {
        this.instruments = instruments;
        this.candles = candles;
        this.signals = signals;
        this.halts = halts;
        this.trades = trades;
        this.paper = paper;
        this.intraday = intraday.toRules(); // valide la configuration au démarrage
        this.events = events;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public boolean enabled() {
        return paper.isEnabled();
    }

    /** Règles intraday en vigueur ({@code null} = désactivées). */
    public IntradayRules intradayRules() {
        return intraday;
    }

    /**
     * Rejeu après un calcul de signaux. Une erreur est journalisée sans interrompre l'ingestion des
     * bougies ni l'enregistrement des signaux (le rejeu suivant repartira de zéro).
     */
    @EventListener
    public void onSignalsRecomputed(SignalsRecomputedEvent e) {
        if (!paper.isEnabled()) {
            return;
        }
        try {
            replay(e.symbol(), e.timeframe());
        } catch (RuntimeException ex) {
            log.error("Rejeu du paper trading {} {} en échec", e.symbol(), Timeframes.label(e.timeframe()), ex);
        }
    }

    /** Rejoue tous les timeframes d'entrée stockés pour {@code symbol}. */
    public ReplayResult replay(String symbol) {
        Instrument ins = instruments.require(symbol);
        List<TimeframeReplay> out = new ArrayList<>();
        for (int seconds : candles.timeframes(ins.id())) {
            Duration tf = Duration.ofSeconds(seconds);
            if (Timeframes.isEntryTimeframe(tf)) {
                out.add(replayTimeframe(ins, tf));
            }
        }
        return new ReplayResult(ins.symbol(), out);
    }

    public ReplayResult replay(String symbol, Duration timeframe) {
        Instrument ins = instruments.require(symbol);
        return new ReplayResult(ins.symbol(), List.of(replayTimeframe(ins, timeframe)));
    }

    private TimeframeReplay replayTimeframe(Instrument ins, Duration tf) {
        String label = Timeframes.label(tf);
        if (!paper.isEnabled()) {
            return new TimeframeReplay(label, 0, 0, 0, 0, 0, 0, 0, 0, 0, "paper trading désactivé");
        }
        TimeframeReplay r = tx.execute(status -> {
            trades.lockInstrument(ins.id());
            return sync(ins, tf, label);
        });
        log.info("Paper trading {} {} : {} signaux ({} exclus par le coupe-circuit), {} trades dont {} ouverts ; "
                        + "{} nouveaux, {} modifiés, {} supprimés", ins.symbol(), label, r.signals(),
                r.excludedByHalt(), r.trades(), r.open(), r.inserted(), r.updated(), r.deleted());
        return r;
    }

    private TimeframeReplay sync(Instrument ins, Duration tf, String label) {
        CandleSeries ltf = candles.load(ins.id(), tf);
        List<SignalRecord> all = signals.findEngineSignals(ins.symbol(), label);
        List<TradingHalt> periods = halts.findAll();

        List<Signal> kept = new ArrayList<>();
        Map<Signal, SignalRecord> origin = new IdentityHashMap<>();
        int excluded = 0;
        for (SignalRecord s : all) {
            if (TradingHaltService.halted(periods, s.time())) {
                excluded++;
                continue;
            }
            Signal sig = s.toEngine();
            kept.add(sig);
            origin.put(sig, s);
        }

        List<Values> computed = new ArrayList<>();
        int open = 0;
        if (ltf.size() >= 2 && !kept.isEmpty()) {
            double cost = paper.costFor(ins.symbol());
            List<Trade> result = Backtester.run(ltf, kept, cost, paper.getMaxHoldBars(), intraday);
            long[] times = ltf.timesMillis();
            for (Trade t : result) {
                Values v = toValues(t, origin.get(t.signal()), times, cost, ins.pointValue());
                if (v.status() == PaperTradeStatus.OPEN) {
                    open++;
                }
                computed.add(v);
            }
        }

        Map<String, PaperTrade> existing = new HashMap<>();
        for (PaperTrade t : trades.findFor(ins.id(), label)) {
            existing.put(key(t.signalTime(), t.direction().pyName()), t);
        }
        Instant now = clock.instant();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        for (Values v : computed) {
            PaperTrade old = existing.remove(key(v.signalTime(), v.direction().pyName()));
            long id;
            if (old == null) {
                id = trades.insert(ins.id(), label, v, now);
                inserted++;
            } else if (!v.sameAs(old)) {
                id = old.id();
                trades.update(id, v, now);
                updated++;
            } else {
                unchanged++;
                continue;
            }
            if (v.status() == PaperTradeStatus.CLOSED && (old == null || old.status() != PaperTradeStatus.CLOSED)) {
                events.publishEvent(new PaperTradeClosedEvent(trades.findById(id).orElseThrow()));
            }
        }
        int deleted = trades.delete(existing.values().stream().map(PaperTrade::id).toList());
        if (deleted > 0) {
            log.warn("Paper trading {} {} : {} trade(s) supprimé(s), le rejeu ne les produit plus", ins.symbol(),
                    label, deleted);
        }
        return new TimeframeReplay(label, ltf.size(), all.size(), excluded, computed.size(), open, inserted, updated,
                deleted, unchanged, null);
    }

    /** Valeurs à stocker pour un trade du moteur (statut, dimensionnement). */
    private Values toValues(Trade t, SignalRecord s, long[] times, double cost, double pointValue) {
        int entryIdx = Arrays.binarySearch(times, t.entryTime().toEpochMilli());
        int exitIdx = Arrays.binarySearch(times, t.exitTime().toEpochMilli());
        boolean open = t.outcome() == Outcome.TIMEOUT && exitIdx == times.length - 1
                && exitIdx - entryIdx < paper.getMaxHoldBars();
        double risk = t.signal().direction().sign() > 0 ? t.entry() - t.signal().stop()
                : t.signal().stop() - t.entry();
        PositionSizer.Size size = PositionSizer.size(paper.getCapital(), paper.getRiskPercent(), risk, pointValue);
        double pnl = t.r() * risk * pointValue * size.quantity();
        return new Values(s.id(), s.time(), s.direction(), open ? PaperTradeStatus.OPEN : PaperTradeStatus.CLOSED,
                t.entryTime(), t.entry(), s.stop(), s.target(), risk,
                open ? null : t.exitTime(), open ? null : t.exit(), open ? null : t.outcome(),
                t.exitTime(), t.exit(), t.r(), cost, size.quantity(), pointValue, pnl, size.riskTooLarge());
    }

    private static String key(Instant signalTime, String direction) {
        return signalTime.toEpochMilli() + "/" + direction;
    }
}
