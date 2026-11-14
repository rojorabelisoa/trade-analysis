package com.srliq.app.news;

import java.time.Instant;

/** Annonce économique (heure UTC). Seules les annonces d'impact "high" bloquent les entrées. */
public record NewsEvent(long id, Instant time, String currency, String impact, String event) {
}
