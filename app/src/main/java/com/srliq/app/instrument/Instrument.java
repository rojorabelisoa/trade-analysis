package com.srliq.app.instrument;

/** Contrat suivi. {@code pointValue} = dollars par point de prix et par contrat (GC 100, MGC 10…). */
public record Instrument(long id, String symbol, String name, double pointValue) {
}
