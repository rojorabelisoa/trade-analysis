package com.srliq.engine.model;

import java.time.Instant;

/** Trade simulé par le backtest. {@code r} = résultat en multiples du risque. */
public record Trade(
        Signal signal,
        Instant entryTime,
        double entry,
        Instant exitTime,
        double exit,
        Outcome outcome,
        double r) {
}
