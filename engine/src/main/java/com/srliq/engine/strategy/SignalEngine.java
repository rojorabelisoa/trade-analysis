package com.srliq.engine.strategy;

import com.srliq.engine.filter.BiasBook;
import com.srliq.engine.filter.NewsCalendar;
import com.srliq.engine.filter.Sessions;
import com.srliq.engine.indicator.Indicators;
import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Signal;
import com.srliq.engine.structure.HtfTrend;
import com.srliq.engine.structure.OrderBlock;
import com.srliq.engine.structure.Pivot;
import com.srliq.engine.structure.PivotKind;
import com.srliq.engine.structure.Pivots;
import com.srliq.engine.structure.TrendFrame;
import com.srliq.engine.structure.Zone;
import com.srliq.engine.structure.ZoneBook;
import com.srliq.engine.util.PyMath;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de signaux (équivalent de {@code generate_signals}) :
 * tendance HTF -> zone S/R HTF -> liquidation (sweep LTF) -> entrée (CHOCH LTF).
 *
 * <p>Fonction pure : aucune donnée n'est utilisée avant son heure de confirmation
 * (pivots), de connaissance (order blocks) ou de clôture (bougies HTF, ATR HTF).
 */
public final class SignalEngine {

    private SignalEngine() {
    }

    /** Sweep en attente de confirmation. */
    private record Pending(Direction direction, Zone zone, double extreme, int bar, Instant time, double trigger,
                           int touches) {
    }

    /**
     * @param zones   timeframe des zones S/R (ex. H1)
     * @param ltf     timeframe d'exécution (ex. 5m)
     * @param cfg     paramètres ({@code null} = défauts)
     * @param bias    biais ({@code null} = aucun fichier : "neutral")
     * @param news    annonces ({@code null} = aucune)
     * @param trend   timeframe de la tendance (ex. H4) ; {@code null} = tendance lue sur {@code zones}
     */
    public static List<Signal> generate(CandleSeries zones, CandleSeries ltf, String symbol, StrategyConfig cfg,
                                        BiasBook bias, NewsCalendar news, CandleSeries trend) {
        if (cfg == null) {
            cfg = StrategyConfig.defaults();
        }
        if (bias == null) {
            bias = BiasBook.none(symbol);
        }
        if (news == null) {
            news = NewsCalendar.none();
        }
        final CandleSeries htf = zones;

        Duration htfDur = htf.barDuration();
        Duration ltfDur = ltf.barDuration();
        long htfDurMs = htfDur.toMillis();
        long ltfDurMs = ltfDur.toMillis();

        // ATR HTF connu seulement à la clôture de la bougie HTF (valeurs NaN retirées).
        double[] htfAtr = Indicators.atr(htf, cfg.atrPeriod());
        int nKnown = 0;
        for (double v : htfAtr) {
            if (!Double.isNaN(v)) {
                nKnown++;
            }
        }
        long[] htfAtrTimes = new long[nKnown];
        double[] htfAtrVals = new double[nKnown];
        for (int i = 0, k = 0; i < htfAtr.length; i++) {
            if (!Double.isNaN(htfAtr[i])) {
                htfAtrTimes[k] = htf.timeMillis(i) + htfDurMs;
                htfAtrVals[k] = htfAtr[i];
                k++;
            }
        }
        double[] aLtf = Indicators.atr(ltf, cfg.atrPeriod());

        List<Pivot> htfPivots = Pivots.find(htf, cfg.htfPivotLeft(), cfg.htfPivotRight(), htfDur);
        TrendFrame trendDf;
        if (trend == null) {
            trendDf = HtfTrend.compute(htf, htfPivots, htfDur, cfg.trendLen(), cfg.tpLookback(), cfg.atrPeriod());
        } else {
            Duration tDur = trend.barDuration();
            List<Pivot> tPiv = Pivots.find(trend, cfg.htfPivotLeft(), cfg.htfPivotRight(), tDur);
            trendDf = HtfTrend.compute(trend, tPiv, tDur, cfg.trendLen(), cfg.tpLookback(), cfg.atrPeriod());
        }
        List<Pivot> ltfPivots = Pivots.find(ltf, cfg.ltfPivotLeft(), cfg.ltfPivotRight(), ltfDur);

        ZoneBook book = new ZoneBook(htfDur.multipliedBy(cfg.zoneMaxAgeBars()));
        List<OrderBlock> obsAll = OrderBlock.find(htf, htfDur);
        int obI = 0;
        List<OrderBlock> liveObs = new ArrayList<>();
        int hpI = 0;
        int lpI = 0;
        Pivot lastLtfHigh = null;
        Pivot lastLtfLow = null;

        Pending pending = null;
        List<Signal> signals = new ArrayList<>();

        double[] h = ltf.highs();
        double[] l = ltf.lows();
        double[] c = ltf.closes();
        int n = ltf.size();

        for (int i = 1; i < n; i++) {
            long closeMs = ltf.timeMillis(i) + ltfDurMs; // instant où la bougie i est connue
            Instant closeTime = Instant.ofEpochMilli(closeMs);

            // Dernier ATR HTF disponible à cet instant (bougie HTF clôturée)
            int k = PyMath.searchRight(htfAtrTimes, closeMs) - 1;
            Double aHtf = k >= 0 ? htfAtrVals[k] : null;

            // Pivots HTF confirmés -> zones
            if (aHtf != null) {
                book.setMaxWidth(cfg.zoneMaxWidthAtr() * aHtf);
            }
            while (hpI < htfPivots.size() && htfPivots.get(hpI).confirmedAtMillis() <= closeMs) {
                if (aHtf != null) {
                    book.add(htfPivots.get(hpI), cfg.zoneMergeAtr() * aHtf);
                }
                hpI++;
            }
            book.prune(closeTime);

            // Order blocks connus, puis mitigés dès que le prix les touche
            while (obI < obsAll.size() && obsAll.get(obI).knownAt().toEpochMilli() <= closeMs) {
                liveObs.add(obsAll.get(obI));
                obI++;
            }
            int from = Math.max(0, liveObs.size() - 60);
            List<OrderBlock> keptObs = new ArrayList<>(liveObs.size() - from);
            for (int j = from; j < liveObs.size(); j++) {
                OrderBlock ob = liveObs.get(j);
                boolean mitigated = (ob.dir() == -1 && h[i] >= ob.bot()) || (ob.dir() == 1 && l[i] <= ob.top());
                if (!mitigated) {
                    keptObs.add(ob);
                }
            }
            liveObs = keptObs;

            // Pivots LTF confirmés -> structure courte
            while (lpI < ltfPivots.size() && ltfPivots.get(lpI).confirmedAtMillis() <= closeMs) {
                Pivot p = ltfPivots.get(lpI);
                if (p.kind() == PivotKind.HIGH) {
                    lastLtfHigh = p;
                } else {
                    lastLtfLow = p;
                }
                lpI++;
            }

            if (aHtf == null || Double.isNaN(aLtf[i])) {
                continue;
            }

            // Tendance HTF en vigueur (dernière bougie HTF clôturée)
            int kt = trendDf.indexAt(closeMs);
            int tr;
            if (cfg.trendMode() == TrendMode.OFF || kt < 0) {
                tr = 0;
            } else if (cfg.trendMode() == TrendMode.STRUCTURE) {
                tr = trendDf.bos(kt);
            } else if (cfg.trendMode() == TrendMode.STRICT) {
                tr = trendDf.strict(kt);
            } else {
                tr = c[i] > trendDf.ema(kt) ? 1 : -1;
            }
            boolean allowLong = (cfg.trendMode() == TrendMode.OFF || tr == 1) && cfg.direction() != DirectionFilter.SHORT;
            boolean allowShort = (cfg.trendMode() == TrendMode.OFF || tr == -1) && cfg.direction() != DirectionFilter.LONG;

            // 1) Confirmation d'un sweep en attente
            if (pending != null) {
                if (i - pending.bar() > cfg.confirmBars()) {
                    pending = null;
                } else {
                    Direction d = pending.direction();
                    // le sweep est invalidé si le prix va au-delà de son extrême
                    if ((d == Direction.LONG && l[i] < pending.extreme())
                            || (d == Direction.SHORT && h[i] > pending.extreme())) {
                        pending = null;
                    } else if ((d == Direction.LONG && c[i] > pending.trigger())
                            || (d == Direction.SHORT && c[i] < pending.trigger())) {
                        boolean okDir = d == Direction.LONG ? allowLong : allowShort;
                        Signal sig = okDir
                                ? buildSignal(pending, c[i], aLtf[i], aHtf, closeTime, symbol, cfg, bias, news, book,
                                kt >= 0 ? trendDf : null, kt, liveObs)
                                : null;
                        if (sig != null) {
                            signals.add(sig);
                        }
                        pending = null; // abandonné même si le signal est refusé
                        continue;
                    }
                }
            }

            // 2) Détection d'un nouveau sweep
            if (pending == null) {
                pending = detectSweep(book.active(cfg.minTouches()), i, h, l, c, aLtf[i], aHtf, cfg, lastLtfHigh,
                        lastLtfLow, ltf.time(i), allowLong, allowShort);
            }
        }
        return signals;
    }

    private static Pending detectSweep(List<Zone> zones, int i, double[] h, double[] l, double[] c, double aLtf,
                                       double aHtf, StrategyConfig cfg, Pivot lastHigh, Pivot lastLow, Instant t,
                                       boolean allowLong, boolean allowShort) {
        Pending best = null;
        for (Zone z : zones) {
            // Sweep haussier sous la zone
            double pen = z.low() - l[i];
            if (allowLong && c[i - 1] > z.low() && pen >= cfg.sweepMinAtr() * aLtf
                    && pen <= cfg.sweepMaxAtr() * aHtf && c[i] > z.low()
                    && lastHigh != null && lastHigh.price() > c[i]) {
                Pending cand = new Pending(Direction.LONG, z, l[i], i, t, lastHigh.price(), z.touches());
                if (best == null || z.touches() > best.touches()) {
                    best = cand;
                }
            }
            // Sweep baissier au-dessus de la zone
            pen = h[i] - z.high();
            if (allowShort && c[i - 1] < z.high() && pen >= cfg.sweepMinAtr() * aLtf
                    && pen <= cfg.sweepMaxAtr() * aHtf && c[i] < z.high()
                    && lastLow != null && lastLow.price() < c[i]) {
                Pending cand = new Pending(Direction.SHORT, z, h[i], i, t, lastLow.price(), z.touches());
                if (best == null || z.touches() > best.touches()) {
                    best = cand;
                }
            }
        }
        return best;
    }

    private static boolean ok(double x) {
        return !Double.isNaN(x);
    }

    private static Signal buildSignal(Pending pending, double close, double aLtf, double aHtf, Instant closeTime,
                                      String symbol, StrategyConfig cfg, BiasBook bias, NewsCalendar news,
                                      ZoneBook book, TrendFrame ctx, int kt, List<OrderBlock> obs) {
        Direction d = pending.direction();
        if (!bias.allows(closeTime, d, cfg.neutralAllowsBoth())) {
            return null;
        }
        if (news.blocked(closeTime).isPresent()) {
            return null;
        }
        String sess = Sessions.sessionOf(closeTime);
        if (cfg.hasSessionFilter() && !cfg.sessions().contains(sess)) {
            return null;
        }

        int sign = d.sign();
        double buf = cfg.slBufferAtr() * aLtf;
        double stop = pending.extreme() - sign * buf;
        double capAtr = aHtf;
        if (cfg.stopMode() == StopMode.H4 && ctx != null) {
            double struct = d == Direction.LONG ? ctx.lastL(kt) : ctx.lastH(kt);
            if (ok(struct)) {
                double stopH4 = struct - sign * buf;
                if (sign * (stop - stopH4) > 0) { // le creux/sommet H4 est plus loin que la mèche
                    stop = stopH4;
                }
            }
            if (ok(ctx.atr(kt))) {
                capAtr = ctx.atr(kt);
            }
        }
        double risk = sign * (close - stop);
        if (risk <= 0 || risk > cfg.maxRiskHtfAtr() * capAtr) {
            return null;
        }
        double target;
        if (cfg.targetMode() == TargetMode.H4) {
            if (ctx == null) {
                return null;
            }
            target = d == Direction.LONG ? ctx.hh(kt) : ctx.ll(kt);
            if (!ok(target) || sign * (target - close) / risk < cfg.minRr()) {
                return null;
            }
        } else if (cfg.targetMode() == TargetMode.OB) {
            if (d == Direction.LONG) {
                double best = Double.NaN;
                for (OrderBlock ob : obs) {
                    if (ob.dir() == -1 && ob.bot() > close && (Double.isNaN(best) || ob.bot() < best)) {
                        best = ob.bot();
                    }
                }
                target = !Double.isNaN(best) ? best : close + cfg.rr() * risk;
            } else {
                double best = Double.NaN;
                for (OrderBlock ob : obs) {
                    if (ob.dir() == 1 && ob.top() < close && (Double.isNaN(best) || ob.top() > best)) {
                        best = ob.top();
                    }
                }
                target = !Double.isNaN(best) ? best : close - cfg.rr() * risk;
            }
            if (sign * (target - close) / risk < cfg.minRr()) {
                return null;
            }
        } else {
            target = close + sign * cfg.rr() * risk;
        }
        double rrReal = sign * (target - close) / risk;

        Double nextZone = null;
        if (d == Direction.LONG) {
            for (Zone z : book.active(cfg.minTouches())) {
                if (z.low() > close && (nextZone == null || z.low() < nextZone)) {
                    nextZone = z.low();
                }
            }
        } else {
            for (Zone z : book.active(cfg.minTouches())) {
                if (z.high() < close && (nextZone == null || z.high() > nextZone)) {
                    nextZone = z.high();
                }
            }
        }

        return new Signal(symbol, d, closeTime, close, stop, target, PyMath.pyRound(rrReal, 2),
                pending.zone().describe(), pending.time(), pending.extreme(), bias.at(closeTime), sess, nextZone);
    }
}
