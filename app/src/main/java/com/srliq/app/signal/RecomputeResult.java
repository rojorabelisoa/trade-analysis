package com.srliq.app.signal;

import java.util.List;

/** Résultat d'un recalcul des signaux d'un instrument, par timeframe d'entrée. */
public record RecomputeResult(String symbol, List<TimeframeResult> timeframes) {

    /**
     * @param candles   bougies du timeframe d'entrée utilisées
     * @param generated signaux produits par le moteur sur tout l'historique
     * @param recorded  signaux nouveaux (les autres étaient déjà en base)
     * @param skipped   raison si le calcul n'a pas été fait, sinon {@code null}
     */
    public record TimeframeResult(String timeframe, int candles, int generated, int recorded, String skipped) {
    }

    public int recorded() {
        return timeframes.stream().mapToInt(TimeframeResult::recorded).sum();
    }
}
