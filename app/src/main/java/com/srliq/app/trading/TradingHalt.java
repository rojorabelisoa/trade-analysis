package com.srliq.app.trading;

import java.time.Instant;

/** Période d'arrêt du coupe-circuit : {@code [startedAt, endedAt)} ; {@code endedAt == null} = en cours. */
public record TradingHalt(long id, Instant startedAt, Instant endedAt, String reason) {

    public boolean active() {
        return endedAt == null;
    }

    /** {@code t} tombe dans la période (début inclus, fin exclue). */
    public boolean covers(Instant t) {
        return !t.isBefore(startedAt) && (endedAt == null || t.isBefore(endedAt));
    }
}
