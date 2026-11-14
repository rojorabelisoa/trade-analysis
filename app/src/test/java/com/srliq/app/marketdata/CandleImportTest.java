package com.srliq.app.marketdata;

import com.srliq.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandleImportTest extends IntegrationTest {

    private static final String SMALL = """
            time,open,high,low,close,Volume
            1767571200,2000,2001,1999,2000.5,1
            1767571500,2000.5,2002,2000,2001,2
            1767571800,2001,2003,2000.5,2002,3
            """;

    @Test
    void importIsIdempotent() throws Exception {
        byte[] csv = bytes(parity("candles/demo_5m.csv"));

        importCsv("GC", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.timeframe").value("5m"))
                .andExpect(jsonPath("$.received").value(8000))
                .andExpect(jsonPath("$.inserted").value(8000))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.signals.timeframes[0].timeframe").value("5m"))
                .andExpect(jsonPath("$.signals.timeframes[0].recorded").value(33));
        int signals = count("signal");

        importCsv("GC1!", csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("GC"))
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(8000))
                .andExpect(jsonPath("$.signals.timeframes[0].generated").value(33))
                .andExpect(jsonPath("$.signals.timeframes[0].recorded").value(0));

        assertThat(count("candle")).isEqualTo(8000);
        assertThat(count("signal")).isEqualTo(signals);
    }

    @Test
    void storesUtcOpenTimesAndUpdatesChangedCandles() throws Exception {
        importCsv("MGC", SMALL.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.signals.timeframes[0].skipped").value("pas assez de bougies"));
        Instant first = jdbc.sql("SELECT min(open_time) FROM candle").query(java.time.OffsetDateTime.class)
                .single().toInstant();
        assertThat(first).isEqualTo(Instant.ofEpochSecond(1767571200));
        assertThat(jdbc.sql("SELECT DISTINCT timeframe_seconds FROM candle").query(Integer.class).single())
                .isEqualTo(300);

        String corrected = SMALL.replace("2001,2003,2000.5,2002,3", "2001,2003,2000.5,2002.25,3");
        importCsv("MGC", corrected.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.unchanged").value(2));
        assertThat(jdbc.sql("SELECT close FROM candle ORDER BY open_time DESC LIMIT 1").query(Double.class).single())
                .isEqualTo(2002.25);
    }

    @Test
    void candleNotYetClosedIsNotStored() throws Exception {
        // La dernière bougie (ouverte à 00:10, clôture à 00:15) n'est pas terminée à 00:12.
        clock.set(Instant.ofEpochSecond(1767571800 + 120));
        importCsv("GC", SMALL.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(3))
                .andExpect(jsonPath("$.skippedOpen").value(1))
                .andExpect(jsonPath("$.inserted").value(2));
        assertThat(count("candle")).isEqualTo(2);
    }

    @Test
    void utf8BomIsAccepted() throws Exception {
        importCsv("GC", ("﻿" + SMALL).getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(3));
    }

    @Test
    void unknownSymbolIsRejected() throws Exception {
        importCsv("ZZ", SMALL.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Instrument inconnu : 'ZZ'"));
        assertThat(count("candle")).isZero();
    }

    @Test
    void invalidCsvIsRejected() throws Exception {
        importCsv("GC", "time,open,high\n1,2,3\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        importCsv("GC", "time,open,high,low,close\n1767571200,1,2,0.5,1\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        importCsv("GC", new byte[0]).andExpect(status().isBadRequest());
        assertThat(count("candle")).isZero();
    }

    @Test
    void missingSymbolIsRejected() throws Exception {
        mvc.perform(multipart("/api/candles/import")
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", SMALL.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
