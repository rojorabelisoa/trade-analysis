package com.srliq.app.marketdata;

import java.time.Duration;

/** Libellés de timeframe : 300 s -> "5m", 3600 s -> "1h", 86400 s -> "1d". */
public final class Timeframes {

    public static final Duration ZONES = Duration.ofHours(1);
    public static final Duration TREND = Duration.ofHours(4);

    private Timeframes() {
    }

    public static String label(Duration d) {
        long s = d.toSeconds();
        if (s > 0 && s % 86_400 == 0) {
            return s / 86_400 + "d";
        }
        if (s > 0 && s % 3_600 == 0) {
            return s / 3_600 + "h";
        }
        if (s > 0 && s % 60 == 0) {
            return s / 60 + "m";
        }
        return s + "s";
    }

    /**
     * Timeframe d'entrée exploitable par le moteur : strictement inférieur à H1 et diviseur
     * de l'heure (1m, 5m, 15m, 30m…), pour que l'agrégation H1/H4 ait un sens.
     */
    public static boolean isEntryTimeframe(Duration d) {
        long ms = d.toMillis();
        return ms > 0 && ms < ZONES.toMillis() && ZONES.toMillis() % ms == 0;
    }
}
