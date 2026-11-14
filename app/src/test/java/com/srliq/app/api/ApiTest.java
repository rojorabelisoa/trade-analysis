package com.srliq.app.api;

import com.srliq.app.signal.SignalRecord;
import com.srliq.app.signal.SignalRecorder;
import com.srliq.app.signal.SignalSource;
import com.srliq.app.support.IntegrationTest;
import com.srliq.engine.model.Direction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiTest extends IntegrationTest {

    @Autowired
    SignalRecorder recorder;

    @Test
    void listsInstrumentsWithPointValues() throws Exception {
        mvc.perform(get("/api/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.symbol=='GC')].pointValue").value(100.0))
                .andExpect(jsonPath("$[?(@.symbol=='MGC')].pointValue").value(10.0))
                .andExpect(jsonPath("$[?(@.symbol=='SI')].pointValue").value(5000.0))
                .andExpect(jsonPath("$[?(@.symbol=='HG')].pointValue").value(25000.0))
                .andExpect(jsonPath("$[?(@.symbol=='CL')].pointValue").value(1000.0));
    }

    @Test
    void biasCrud() throws Exception {
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-12\",\"symbol\":\"gc1!\",\"bias\":\"LONG\",\"note\":\"Fed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.effectiveDate").value("2026-01-12"))
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.bias").value("long"));
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-05\",\"symbol\":\"SI\",\"bias\":\"neutral\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/bias")).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("SI"));
        mvc.perform(get("/api/bias").param("symbol", "GC")).andExpect(jsonPath("$", hasSize(1)));

        // Même instrument, même date : conflit.
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-12\",\"symbol\":\"GC\",\"bias\":\"short\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(delete("/api/bias/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/bias/1")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/bias")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void biasValidation() throws Exception {
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-12\",\"symbol\":\"GC\",\"bias\":\"bullish\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0]", containsString("bias")));
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"GC\",\"bias\":\"long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("date")));
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"12/01/2026\",\"symbol\":\"GC\",\"bias\":\"long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-01-12\",\"symbol\":\"ES\",\"bias\":\"long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Instrument inconnu : 'ES'"));
        mvc.perform(post("/api/bias").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void newsCrudAndValidation() throws Exception {
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":\"2026-01-13T13:30:00Z\",\"currency\":\"usd\",\"impact\":\"High\","
                                + "\"event\":\"CPI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.time").value("2026-01-13T13:30:00Z"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.impact").value("high"));
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":\"2026-01-28T19:00:00Z\",\"currency\":\"USD\",\"impact\":\"high\","
                                + "\"event\":\"FOMC\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/news")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/news").param("from", "2026-01-20T00:00:00Z"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].event").value("FOMC"));
        mvc.perform(get("/api/news").param("from", "hier")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"time\":\"2026-01-13T13:30:00Z\",\"currency\":\"USD\",\"impact\":\"huge\","
                                + "\"event\":\"CPI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("impact")));
        mvc.perform(post("/api/news").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USD\",\"impact\":\"high\",\"event\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));

        mvc.perform(delete("/api/news/2")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/news/2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/news")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void signalsFilterAndPagination() throws Exception {
        Instant t0 = Instant.parse("2026-01-17T08:00:00Z");
        for (int i = 0; i < 5; i++) {
            recorder.record(signal("GC", SignalSource.ENGINE, t0.plusSeconds(300L * i)));
        }
        recorder.record(signal("SI", SignalSource.ENGINE, t0));
        recorder.record(signal("GC", SignalSource.TRADINGVIEW, t0));

        mvc.perform(get("/api/signals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.items", hasSize(7)));
        mvc.perform(get("/api/signals").param("symbol", "GC").param("source", "ENGINE")
                        .param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].time").value("2026-01-17T08:10:00Z"))
                .andExpect(jsonPath("$.items[1].time").value("2026-01-17T08:05:00Z"));
        mvc.perform(get("/api/signals").param("from", "2026-01-17T08:05:00Z").param("to", "2026-01-17T08:15:00Z"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/signals").param("source", "TRADINGVIEW"))
                .andExpect(jsonPath("$.items[*].source", hasItem("TRADINGVIEW")))
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(get("/api/signals").param("source", "PYTHON")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/signals").param("size", "0")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/signals").param("size", "501")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/signals").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/signals").param("symbol", "XX")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/signals").param("from", "2026-01-18T00:00:00Z").param("to", "2026-01-17T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actuatorExposesHealthOnly() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRouteIsProblemJson() throws Exception {
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static SignalRecord signal(String symbol, SignalSource source, Instant time) {
        return new SignalRecord(null, symbol, source, "5m", time, Direction.LONG, 2000, 1995, 2010, 2.0,
                "1995.0-1996.0 (2 contacts, pivots low)", null, time.minusSeconds(600), 1994.5, "neutral",
                "londres", null, NOW);
    }
}
