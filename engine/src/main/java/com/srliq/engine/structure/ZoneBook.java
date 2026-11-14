package com.srliq.engine.structure;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Zones S/R construites au fil de l'eau à partir des pivots HTF confirmés ({@code ZoneBook}).
 * Un pivot à moins de {@code mergeTol} d'une zone l'élargit (si la largeur reste
 * {@code <= maxWidth}) et ajoute un contact ; sinon il crée une zone. Les zones plus anciennes
 * que {@code maxAge} sont oubliées.
 */
public final class ZoneBook {

    private List<Zone> zones = new ArrayList<>();
    private final Duration maxAge;
    private Double maxWidth;

    public ZoneBook(Duration maxAge, Double maxWidth) {
        this.maxAge = maxAge;
        this.maxWidth = maxWidth;
    }

    public ZoneBook(Duration maxAge) {
        this(maxAge, null);
    }

    public Double maxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(Double maxWidth) {
        this.maxWidth = maxWidth;
    }

    public List<Zone> zones() {
        return Collections.unmodifiableList(zones);
    }

    public void add(Pivot pivot, double mergeTol) {
        Zone best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        double price = pivot.price();
        for (Zone z : zones) {
            if (z.low() - mergeTol <= price && price <= z.high() + mergeTol) {
                double d = Math.abs(price - z.mid());
                if (d < bestDist) {
                    best = z;
                    bestDist = d;
                }
            }
        }
        if (best != null) {
            best.absorb(pivot, maxWidth);
            return;
        }
        zones.add(new Zone(price, price, 1, pivot.time(), pivot.time(), Set.of(pivot.kind().pyName())));
    }

    public void prune(Instant now) {
        List<Zone> kept = new ArrayList<>(zones.size());
        for (Zone z : zones) {
            if (Duration.between(z.lastTime(), now).compareTo(maxAge) <= 0) {
                kept.add(z);
            }
        }
        zones = kept;
    }

    public List<Zone> active(int minTouches) {
        List<Zone> out = new ArrayList<>();
        for (Zone z : zones) {
            if (z.touches() >= minTouches) {
                out.add(z);
            }
        }
        return out;
    }
}
