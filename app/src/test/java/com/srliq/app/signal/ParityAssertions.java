package com.srliq.app.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.srliq.app.support.IntegrationTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Comparaison des signaux enregistrés par l'application avec une fixture de la référence Python. */
final class ParityAssertions {

    private static final double PRICE_TOL = 1e-6;

    private ParityAssertions() {
    }

    static JsonNode fixture(String name) {
        try {
            return new ObjectMapper().readTree(IntegrationTest.parity(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<SignalRecord> engineSignals(SignalRepository repo, String symbol) {
        List<SignalRecord> out = new ArrayList<>(
                repo.search(new SignalRepository.Filter(symbol, SignalSource.ENGINE, null, null), 0, 10_000).items());
        out.sort(Comparator.comparing(SignalRecord::time).thenComparing(s -> s.direction().pyName()));
        return out;
    }

    static void assertSameSignals(JsonNode expected, List<SignalRecord> actual) {
        assertSameSignals(expected, actual, "5m");
    }

    static void assertSameSignals(JsonNode expected, List<SignalRecord> actual, String timeframe) {
        List<JsonNode> exp = new ArrayList<>();
        expected.forEach(exp::add);
        exp.sort(Comparator.comparing((JsonNode n) -> n.get("time").asText())
                .thenComparing(n -> n.get("direction").asText()));
        assertThat(actual).as("nombre de signaux").hasSize(exp.size());
        for (int k = 0; k < exp.size(); k++) {
            JsonNode e = exp.get(k);
            SignalRecord a = actual.get(k);
            String where = "signal #" + k + " " + e.get("time").asText();
            assertThat(a.time().toString()).as(where + " heure").isEqualTo(e.get("time").asText());
            assertThat(a.direction().pyName()).as(where + " sens").isEqualTo(e.get("direction").asText());
            assertThat(a.entry()).as(where + " entrée").isCloseTo(e.get("entry").asDouble(), within(PRICE_TOL));
            assertThat(a.stop()).as(where + " stop").isCloseTo(e.get("stop").asDouble(), within(PRICE_TOL));
            assertThat(a.target()).as(where + " objectif").isCloseTo(e.get("target").asDouble(), within(PRICE_TOL));
            assertThat(a.rr()).as(where + " rr").isCloseTo(e.get("rr").asDouble(), within(1e-9));
            assertThat(a.zone()).as(where + " zone").isEqualTo(e.get("zone").asText());
            assertThat(a.sweepTime().toString()).as(where + " sweep").isEqualTo(e.get("sweep_time").asText());
            assertThat(a.sweepExtreme()).as(where + " extrême")
                    .isCloseTo(e.get("sweep_extreme").asDouble(), within(PRICE_TOL));
            assertThat(a.bias()).as(where + " biais").isEqualTo(e.get("bias").asText());
            assertThat(a.session()).as(where + " session").isEqualTo(e.get("session").asText());
            JsonNode nz = e.get("next_zone");
            if (nz == null || nz.isNull()) {
                assertThat(a.nextZone()).as(where + " next_zone").isNull();
            } else {
                assertThat(a.nextZone()).as(where + " next_zone").isCloseTo(nz.asDouble(), within(PRICE_TOL));
            }
            assertThat(a.source()).isEqualTo(SignalSource.ENGINE);
            assertThat(a.timeframe()).isEqualTo(timeframe);
        }
    }
}
