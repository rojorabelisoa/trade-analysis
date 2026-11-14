package com.srliq.app.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Lecture des fixtures JSON de la référence Python. */
public final class Fixtures {

    private Fixtures() {
    }

    public static JsonNode fixture(String name) {
        try {
            return new ObjectMapper().readTree(IntegrationTest.parity(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Variante {@code backtests[]} de la fixture ayant ce réglage intraday. */
    public static JsonNode backtest(String name, boolean intraday) {
        for (JsonNode b : fixture(name).get("backtests")) {
            if (b.get("intraday").asBoolean() == intraday) {
                return b;
            }
        }
        throw new IllegalArgumentException("Variante intraday=" + intraday + " absente de " + name);
    }
}
