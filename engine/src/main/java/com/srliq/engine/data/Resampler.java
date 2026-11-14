package com.srliq.engine.data;

import com.srliq.engine.model.CandleSeries;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agrégation vers un timeframe supérieur, comme
 * {@code df.resample(rule, label="left", closed="left")} de la référence : seaux
 * {@code [début, début + rule)} alignés sur minuit UTC du premier jour de la série,
 * étiquetés par leur heure d'ouverture ; seaux vides supprimés.
 * open = premier, high = max, low = min, close = dernier, volume = somme compensée (NaN ignorés).
 */
public final class Resampler {

    private static final long DAY_MS = 86_400_000L;
    private static final Pattern RULE = Pattern.compile("(\\d*)\\s*(min|t|h|d|s)", Pattern.CASE_INSENSITIVE);

    private Resampler() {
    }

    /** Règle pandas : "15min", "1h", "4h", "1d"… */
    public static Duration parseRule(String rule) {
        Matcher m = RULE.matcher(rule.strip().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            throw new IllegalArgumentException("Règle d'agrégation non supportée : " + rule);
        }
        long n = m.group(1).isEmpty() ? 1 : Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "min", "t" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "d" -> Duration.ofDays(n);
            case "s" -> Duration.ofSeconds(n);
            default -> throw new IllegalArgumentException(rule);
        };
    }

    public static CandleSeries resample(CandleSeries src, String rule) {
        return resample(src, parseRule(rule));
    }

    public static CandleSeries resample(CandleSeries src, Duration rule) {
        long step = rule.toMillis();
        if (step <= 0) {
            throw new IllegalArgumentException("Durée d'agrégation invalide : " + rule);
        }
        int n = src.size();
        if (n == 0) {
            return src;
        }
        long origin = Math.floorDiv(src.timeMillis(0), DAY_MS) * DAY_MS; // origin="start_day"
        long[] t = new long[n];
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] c = new double[n];
        double[] v = new double[n];
        double comp = 0.0; // somme compensée (Kahan), comme le groupby sum de pandas
        int k = -1;
        long current = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            long bucket = origin + Math.floorDiv(src.timeMillis(i) - origin, step) * step;
            if (k < 0 || bucket != current) {
                k++;
                current = bucket;
                t[k] = bucket;
                o[k] = src.open(i);
                h[k] = src.high(i);
                l[k] = src.low(i);
                c[k] = src.close(i);
                v[k] = 0.0;
                comp = 0.0;
                if (!Double.isNaN(src.volume(i))) {
                    double y = src.volume(i) - comp;
                    double s2 = v[k] + y;
                    comp = s2 - v[k] - y;
                    v[k] = s2;
                }
            } else {
                h[k] = Math.max(h[k], src.high(i));
                l[k] = Math.min(l[k], src.low(i));
                c[k] = src.close(i);
                if (!Double.isNaN(src.volume(i))) {
                    double y = src.volume(i) - comp;
                    double s2 = v[k] + y;
                    comp = s2 - v[k] - y;
                    v[k] = s2;
                }
            }
        }
        int m = k + 1;
        return CandleSeries.ofArrays(java.util.Arrays.copyOf(t, m), java.util.Arrays.copyOf(o, m),
                java.util.Arrays.copyOf(h, m), java.util.Arrays.copyOf(l, m), java.util.Arrays.copyOf(c, m),
                java.util.Arrays.copyOf(v, m));
    }
}
