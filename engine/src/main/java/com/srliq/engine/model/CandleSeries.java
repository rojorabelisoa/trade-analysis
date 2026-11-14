package com.srliq.engine.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Série de bougies immuable, triée par heure d'ouverture strictement croissante.
 * Les heures sont stockées en millisecondes Unix (UTC).
 */
public final class CandleSeries {

    private final long[] times;
    private final double[] open;
    private final double[] high;
    private final double[] low;
    private final double[] close;
    private final double[] volume;

    private CandleSeries(long[] times, double[] open, double[] high, double[] low, double[] close,
                         double[] volume) {
        int n = times.length;
        if (open.length != n || high.length != n || low.length != n || close.length != n || volume.length != n) {
            throw new IllegalArgumentException("Colonnes de longueurs différentes");
        }
        for (int i = 1; i < n; i++) {
            if (times[i] <= times[i - 1]) {
                throw new IllegalArgumentException("Horodatages non strictement croissants à l'index " + i);
            }
        }
        this.times = times;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    /** Construit une série à partir de tableaux (copiés). Heures en millisecondes Unix. */
    public static CandleSeries ofArrays(long[] timesMillis, double[] open, double[] high, double[] low,
                                        double[] close, double[] volume) {
        return new CandleSeries(timesMillis.clone(), open.clone(), high.clone(), low.clone(), close.clone(),
                volume.clone());
    }

    public static CandleSeries of(List<Candle> candles) {
        int n = candles.size();
        long[] t = new long[n];
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] c = new double[n];
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            Candle k = candles.get(i);
            t[i] = k.time().toEpochMilli();
            o[i] = k.open();
            h[i] = k.high();
            l[i] = k.low();
            c[i] = k.close();
            v[i] = k.volume();
        }
        return new CandleSeries(t, o, h, l, c, v);
    }

    public int size() {
        return times.length;
    }

    public boolean isEmpty() {
        return times.length == 0;
    }

    public Candle get(int i) {
        return new Candle(Instant.ofEpochMilli(times[i]), open[i], high[i], low[i], close[i], volume[i]);
    }

    public List<Candle> candles() {
        List<Candle> out = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            out.add(get(i));
        }
        return out;
    }

    public Instant time(int i) {
        return Instant.ofEpochMilli(times[i]);
    }

    public long timeMillis(int i) {
        return times[i];
    }

    public double open(int i) {
        return open[i];
    }

    public double high(int i) {
        return high[i];
    }

    public double low(int i) {
        return low[i];
    }

    public double close(int i) {
        return close[i];
    }

    public double volume(int i) {
        return volume[i];
    }

    /** Copies défensives des colonnes. */
    public long[] timesMillis() {
        return times.clone();
    }

    public double[] opens() {
        return open.clone();
    }

    public double[] highs() {
        return high.clone();
    }

    public double[] lows() {
        return low.clone();
    }

    public double[] closes() {
        return close.clone();
    }

    public double[] volumes() {
        return volume.clone();
    }

    /** Sous-série [from, to). */
    public CandleSeries slice(int from, int to) {
        return new CandleSeries(Arrays.copyOfRange(times, from, to), Arrays.copyOfRange(open, from, to),
                Arrays.copyOfRange(high, from, to), Arrays.copyOfRange(low, from, to),
                Arrays.copyOfRange(close, from, to), Arrays.copyOfRange(volume, from, to));
    }

    /** Bougies dont l'heure d'ouverture est strictement antérieure à {@code t}. */
    public CandleSeries before(Instant t) {
        long x = t.toEpochMilli();
        int n = 0;
        while (n < times.length && times[n] < x) {
            n++;
        }
        return slice(0, n);
    }

    /**
     * Durée d'une bougie = écart le plus fréquent entre deux horodatages ; le plus petit
     * en cas d'égalité (comme {@code infer_bar_duration}).
     */
    public Duration barDuration() {
        if (times.length < 2) {
            throw new IllegalStateException("Pas assez de bougies pour déduire le timeframe");
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (int i = 1; i < times.length; i++) {
            counts.merge(times[i] - times[i - 1], 1, Integer::sum);
        }
        long best = Long.MAX_VALUE;
        int bestCount = -1;
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            int c = e.getValue();
            long d = e.getKey();
            if (c > bestCount || (c == bestCount && d < best)) {
                best = d;
                bestCount = c;
            }
        }
        return Duration.ofMillis(best);
    }
}
