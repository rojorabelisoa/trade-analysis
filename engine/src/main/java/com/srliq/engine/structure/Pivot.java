package com.srliq.engine.structure;

import java.time.Instant;

/**
 * Pivot confirmé.
 *
 * @param time        ouverture de la bougie du pivot
 * @param confirmedAt clôture de la bougie i + right : rien ne peut l'utiliser avant
 */
public record Pivot(PivotKind kind, double price, Instant time, Instant confirmedAt) {

    public long confirmedAtMillis() {
        return confirmedAt.toEpochMilli();
    }
}
