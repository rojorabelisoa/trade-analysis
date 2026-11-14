package com.srliq.app.marketdata;

/** Destination des bougies poussées par une {@link MarketDataSource} (stockage + recalcul des signaux). */
public interface CandleSink {

    IngestResult accept(CandleBatch batch);
}
