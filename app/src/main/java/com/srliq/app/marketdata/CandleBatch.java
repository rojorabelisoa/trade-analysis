package com.srliq.app.marketdata;

import com.srliq.engine.model.CandleSeries;

import java.time.Duration;
import java.util.Objects;

/**
 * Lot de bougies d'un instrument et d'un timeframe, tel que fourni par une {@link MarketDataSource}.
 *
 * @param source    identifiant de la source ("tradingview-csv")
 * @param symbol    symbole (normalisé à l'ingestion : "GC1!" -> "GC")
 * @param timeframe durée d'une bougie
 * @param candles   bougies triées par heure d'ouverture (UTC)
 */
public record CandleBatch(String source, String symbol, Duration timeframe, CandleSeries candles) {
    public CandleBatch {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(candles, "candles");
    }
}
