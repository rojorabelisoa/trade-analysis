package com.srliq.app.signal;

import java.time.Duration;

/**
 * Publié (synchrone, hors transaction) après chaque calcul des signaux d'un instrument sur un
 * timeframe d'entrée, qu'il y ait de nouveaux signaux ou non. Le paper trading s'y abonne pour
 * rejouer le backtest sur l'historique stocké.
 */
public record SignalsRecomputedEvent(String symbol, Duration timeframe) {
}
