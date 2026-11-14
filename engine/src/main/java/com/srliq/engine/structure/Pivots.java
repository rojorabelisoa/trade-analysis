package com.srliq.engine.structure;

import com.srliq.engine.model.CandleSeries;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pivots sans look-ahead ({@code find_pivots}). */
public final class Pivots {

    private Pivots() {
    }

    /**
     * Swing high : high strictement supérieur aux {@code left} bougies avant et supérieur ou égal
     * aux {@code right} bougies après (idem pour les lows). Résultat trié (tri stable) par
     * {@code confirmedAt}.
     */
    public static List<Pivot> find(CandleSeries df, int left, int right, Duration barDuration) {
        if (left < 1 || right < 1) {
            throw new IllegalArgumentException("left et right doivent être >= 1");
        }
        int n = df.size();
        List<Pivot> pivots = new ArrayList<>();
        for (int i = left; i < n - right; i++) {
            double h = df.high(i);
            double hBefore = Double.NEGATIVE_INFINITY;
            for (int j = i - left; j < i; j++) {
                hBefore = Math.max(hBefore, df.high(j));
            }
            double hAfter = Double.NEGATIVE_INFINITY;
            for (int j = i + 1; j < i + 1 + right; j++) {
                hAfter = Math.max(hAfter, df.high(j));
            }
            Instant confirmed = df.time(i + right).plus(barDuration);
            if (h > hBefore && h >= hAfter) {
                pivots.add(new Pivot(PivotKind.HIGH, h, df.time(i), confirmed));
            }
            double lo = df.low(i);
            double lBefore = Double.POSITIVE_INFINITY;
            for (int j = i - left; j < i; j++) {
                lBefore = Math.min(lBefore, df.low(j));
            }
            double lAfter = Double.POSITIVE_INFINITY;
            for (int j = i + 1; j < i + 1 + right; j++) {
                lAfter = Math.min(lAfter, df.low(j));
            }
            if (lo < lBefore && lo <= lAfter) {
                pivots.add(new Pivot(PivotKind.LOW, lo, df.time(i), confirmed));
            }
        }
        pivots.sort(Comparator.comparing(Pivot::confirmedAt)); // List.sort est stable
        return pivots;
    }
}
