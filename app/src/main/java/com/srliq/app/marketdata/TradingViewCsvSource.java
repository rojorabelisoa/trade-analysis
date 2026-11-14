package com.srliq.app.marketdata;

import com.srliq.engine.data.TradingViewCsv;
import com.srliq.engine.model.CandleSeries;
import com.srliq.app.web.ApiException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Source « à la demande » : export CSV TradingView (Chart -> Export chart data), lu par
 * {@link TradingViewCsv} du moteur. Le timeframe est déduit des horodatages
 * ({@link CandleSeries#barDuration()}).
 */
@Component
public class TradingViewCsvSource implements MarketDataSource {

    public static final String ID = "tradingview-csv";

    private final CandleSink sink;

    public TradingViewCsvSource(CandleSink sink) {
        this.sink = sink;
    }

    @Override
    public String id() {
        return ID;
    }

    public IngestResult importCsv(String symbol, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1); // BOM UTF-8 (export Excel)
        }
        CandleSeries series = TradingViewCsv.parse(text);
        if (series.size() < 2) {
            throw ApiException.badRequest("Au moins deux bougies sont nécessaires pour déduire le timeframe");
        }
        Duration tf = series.barDuration();
        return sink.accept(new CandleBatch(ID, symbol, tf, series));
    }
}
