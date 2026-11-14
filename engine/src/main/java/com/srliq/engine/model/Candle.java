package com.srliq.engine.model;

import java.time.Instant;
import java.util.Objects;

/** Bougie OHLCV. {@code time} = heure d'OUVERTURE (UTC). */
public record Candle(Instant time, double open, double high, double low, double close, double volume) {
    public Candle {
        Objects.requireNonNull(time, "time");
    }
}
