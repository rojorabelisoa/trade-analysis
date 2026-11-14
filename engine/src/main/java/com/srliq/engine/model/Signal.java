package com.srliq.engine.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Signal d'entrée (équivalent de la dataclass {@code Signal} de la référence).
 *
 * @param time      clôture de la bougie de confirmation
 * @param entry     prix de référence (clôture de confirmation)
 * @param rr        R:R arrondi à 2 décimales ({@code round()} Python)
 * @param zone      description de la zone ({@code Zone.describe()})
 * @param nextZone  prochaine zone opposée, ou {@code null}
 */
public record Signal(
        String symbol,
        Direction direction,
        Instant time,
        double entry,
        double stop,
        double target,
        double rr,
        String zone,
        Instant sweepTime,
        double sweepExtreme,
        String bias,
        String session,
        Double nextZone) {

    public Signal {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(time, "time");
    }

    /** |entrée - stop|. */
    public double risk() {
        return Math.abs(entry - stop);
    }
}
