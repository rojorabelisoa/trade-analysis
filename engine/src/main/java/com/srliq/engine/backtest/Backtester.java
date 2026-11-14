package com.srliq.engine.backtest;

import com.srliq.engine.model.CandleSeries;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import com.srliq.engine.util.PyMath;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backtest prudent des signaux ({@code run_backtest}) :
 * entrée à l'ouverture de la bougie qui suit le signal ; stop et objectif gardent leur prix ;
 * stop compté si stop et objectif sont touchés dans la même bougie ; un seul trade à la fois ;
 * {@code cost} retiré à chaque trade ; sortie forcée après {@code maxHoldBars} bougies.
 * Résultats en R.
 */
public final class Backtester {

    private Backtester() {
    }

    public static List<Trade> run(CandleSeries ltf, List<Signal> signals, double cost, int maxHoldBars,
                                  IntradayRules intraday) {
        int n = ltf.size();
        long[] times = ltf.timesMillis();
        long bar = n > 1 ? times[1] - times[0] : 0L;
        Map<LocalDate, Integer> tradesPerDay = new HashMap<>();
        Map<LocalDate, Double> rPerDay = new HashMap<>();
        double[] o = ltf.opens();
        double[] h = ltf.highs();
        double[] l = ltf.lows();
        double[] c = ltf.closes();
        List<Trade> trades = new ArrayList<>();
        long busyUntil = Long.MIN_VALUE;

        List<Signal> sorted = new ArrayList<>(signals);
        sorted.sort(Comparator.comparing(Signal::time)); // tri stable
        for (Signal s : sorted) {
            long st = s.time().toEpochMilli();
            if (st < busyUntil) {
                continue;
            }
            LocalDate day = s.time().atZone(ZoneOffset.UTC).toLocalDate();
            if (intraday != null && !(intraday.entryAllowed(s.time())
                    && tradesPerDay.getOrDefault(day, 0) < intraday.maxTradesDay()
                    && rPerDay.getOrDefault(day, 0.0) > -intraday.maxLossDayR())) {
                continue;
            }
            int i = PyMath.searchLeft(times, st); // bougie qui ouvre au signal
            if (i >= n) {
                continue;
            }
            double entry = o[i];
            boolean isLong = s.direction() == Direction.LONG;
            double risk = isLong ? (entry - s.stop()) : (s.stop() - entry);
            if (risk <= 0) { // gap au-delà du stop : trade annulé
                continue;
            }
            Outcome outcome = Outcome.TIMEOUT;
            int last = Math.min(i + maxHoldBars, n - 1);
            double exitPx = c[last];
            int j;
            boolean broke = false;
            int end = Math.min(i + maxHoldBars + 1, n);
            for (j = i; j < end; j++) {
                boolean hitSl = isLong ? l[j] <= s.stop() : h[j] >= s.stop();
                boolean hitTp = isLong ? h[j] >= s.target() : l[j] <= s.target();
                if (hitSl) {
                    outcome = Outcome.SL;
                    exitPx = s.stop();
                    broke = true;
                    break;
                }
                if (hitTp) {
                    outcome = Outcome.TP;
                    exitPx = s.target();
                    broke = true;
                    break;
                }
                if (intraday != null && intraday.mustFlat(Instant.ofEpochMilli(times[j] + bar))) {
                    outcome = Outcome.EOD;
                    exitPx = c[j];
                    broke = true;
                    break;
                }
            }
            if (!broke) { // for ... else de la référence
                j = last;
            }
            double pnl = isLong ? (exitPx - entry) : (entry - exitPx);
            double r = (pnl - cost) / risk;
            Instant exitTime = Instant.ofEpochMilli(times[j]);
            trades.add(new Trade(s, Instant.ofEpochMilli(times[i]), entry, exitTime, exitPx, outcome, r));
            tradesPerDay.merge(day, 1, Integer::sum);
            rPerDay.merge(day, r, Double::sum);
            busyUntil = times[j];
        }
        return trades;
    }
}
