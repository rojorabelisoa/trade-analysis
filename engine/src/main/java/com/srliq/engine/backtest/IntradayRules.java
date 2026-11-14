package com.srliq.engine.backtest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Scalping / intraday : pas de position la nuit, limites journalières (heures UTC).
 * Valeurs par défaut identiques à la référence : entrées de 07:00 à 19:00, à plat à 20:45,
 * 3 trades et -2R maximum par jour.
 */
public record IntradayRules(int entryStartH, int entryEndH, int flatH, int flatM, int maxTradesDay,
                            double maxLossDayR) {

    public static IntradayRules defaults() {
        return new IntradayRules(7, 19, 20, 45, 3, 2.0);
    }

    public IntradayRules withMaxLossDayR(double v) {
        return new IntradayRules(entryStartH, entryEndH, flatH, flatM, maxTradesDay, v);
    }

    public IntradayRules withMaxTradesDay(int v) {
        return new IntradayRules(entryStartH, entryEndH, flatH, flatM, v, maxLossDayR);
    }

    private static int minuteOfDay(Instant ts) {
        ZonedDateTime z = ts.atZone(ZoneOffset.UTC);
        return z.getHour() * 60 + z.getMinute();
    }

    public boolean entryAllowed(Instant ts) {
        int m = minuteOfDay(ts);
        return entryStartH * 60 <= m && m < entryEndH * 60;
    }

    public boolean mustFlat(Instant ts) {
        int m = minuteOfDay(ts);
        return m >= flatH * 60 + flatM || m < entryStartH * 60;
    }
}
