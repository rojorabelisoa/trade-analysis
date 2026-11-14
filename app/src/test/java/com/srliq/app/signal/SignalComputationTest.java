package com.srliq.app.signal;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signaux calculés par l'application (configuration par défaut) contre la fixture Python s1. */
@RecordApplicationEvents
class SignalComputationTest extends IntegrationTest {

    @Autowired
    SignalRepository signals;

    @Autowired
    ApplicationEvents events;

    @Test
    void signalsMatchPythonReferenceAndRecomputeIsIdempotent() throws Exception {
        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());

        ParityAssertions.assertSameSignals(ParityAssertions.fixture("s1_defaut_h4_h1_5m.json").get("signals"),
                ParityAssertions.engineSignals(signals, "GC"));
        assertThat(events.stream(SignalRecordedEvent.class)).hasSize(33);

        mvc.perform(post("/api/signals/recompute").param("symbol", "GC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.timeframes[0].generated").value(33))
                .andExpect(jsonPath("$.timeframes[0].recorded").value(0));
        mvc.perform(post("/api/signals/recompute").param("symbol", "gc1!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframes[0].recorded").value(0));

        assertThat(count("signal")).isEqualTo(33);
        assertThat(events.stream(SignalRecordedEvent.class)).as("aucun événement pour un doublon").hasSize(33);
    }

    @Test
    void fifteenMinuteSignalsMatchPythonReference() throws Exception {
        importCsv("GC", bytes(parity("candles/trend_15m.csv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframe").value("15m"));
        ParityAssertions.assertSameSignals(ParityAssertions.fixture("s5_tendances_15m_defaut.json").get("signals"),
                ParityAssertions.engineSignals(signals, "GC"), "15m");
    }

    @Test
    void recomputeWithoutCandlesDoesNothing() throws Exception {
        mvc.perform(post("/api/signals/recompute").param("symbol", "SI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeframes").isEmpty());
        mvc.perform(post("/api/signals/recompute").param("symbol", "XX"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/signals/recompute"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void biasFromDatabaseIsApplied() throws Exception {
        String id = createBias("{\"date\":\"2026-01-01\",\"symbol\":\"GC\",\"bias\":\"off\",\"note\":\"pause\"}");

        importCsv("GC", bytes(parity("candles/demo_5m.csv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signals.timeframes[0].generated").value(0));
        assertThat(count("signal")).isZero();

        mvc.perform(delete("/api/bias/" + id)).andExpect(status().isNoContent());
        mvc.perform(post("/api/signals/recompute").param("symbol", "GC"))
                .andExpect(jsonPath("$.timeframes[0].recorded").value(33));
    }

    @Test
    void biasOfAnotherInstrumentDoesNotAffectThisOne() throws Exception {
        // Un biais saisi pour l'argent ne doit pas couper les signaux de l'or.
        createBias("{\"date\":\"2026-01-01\",\"symbol\":\"SI\",\"bias\":\"off\"}");
        importCsv("GC", bytes(parity("candles/demo_5m.csv")))
                .andExpect(jsonPath("$.signals.timeframes[0].generated").value(33));
    }

    @Test
    void highImpactNewsFromDatabaseBlocksEntries() throws Exception {
        // Premier signal de s1 : 2026-01-17T08:50Z. Annonce à 09:15 : 08:50 est dans [08:45, 09:45].
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":\"2026-01-17T09:15:00Z\",\"currency\":\"USD\",\"impact\":\"high\","
                                + "\"event\":\"Test\"}"))
                .andExpect(status().isCreated());
        // Une annonce à faible impact ne bloque rien.
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":\"2026-01-18T03:50:00Z\",\"currency\":\"USD\",\"impact\":\"low\","
                                + "\"event\":\"Mineure\"}"))
                .andExpect(status().isCreated());

        importCsv("GC", bytes(parity("candles/demo_5m.csv"))).andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT count(*) FROM signal WHERE signal_time = '2026-01-17T08:50:00Z'")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM signal WHERE signal_time = '2026-01-18T03:50:00Z'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    private String createBias(String json) throws Exception {
        String body = mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll("^\\{\"id\":(\\d+),.*$", "$1");
    }
}
