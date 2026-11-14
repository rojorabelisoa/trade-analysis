package com.srliq.engine.data;

import com.srliq.engine.model.CandleSeries;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingViewCsvTest {

    @Test
    void readsUnixSecondsAndInfersBarDuration() {
        CandleSeries s = TradingViewCsv.parse(
                "time,open,high,low,close,Volume\n1767600000,1,2,0.5,1.5,10\n1767600300,1.5,2,1,1.8,5\n");
        assertEquals(2, s.size());
        assertEquals(Instant.ofEpochSecond(1767600000L), s.time(0));
        assertEquals(10.0, s.volume(0));
        assertEquals(Duration.ofMinutes(5), s.barDuration());
    }

    @Test
    void readsIsoTimesAndDefaultsVolumeToZero() {
        CandleSeries s = TradingViewCsv.parse("time,open,high,low,close\n2026-01-05T00:00:00Z,1,2,0.5,1.5\n");
        assertEquals(1, s.size());
        assertEquals(0.0, s.volume(0));
        assertEquals(Instant.parse("2026-01-05T00:00:00Z"), s.time(0));
    }

    @Test
    void isoWithOffsetIsConvertedToUtcAndNaiveIsUtc() {
        CandleSeries s = TradingViewCsv.parse(
                "time,open,high,low,close\n2026-01-05T01:00:00+01:00,1,2,0.5,1.5\n2026-01-05 00:05,1,2,0.5,1.5\n");
        assertEquals(Instant.parse("2026-01-05T00:00:00Z"), s.time(0));
        assertEquals(Instant.parse("2026-01-05T00:05:00Z"), s.time(1));
    }

    @Test
    void columnsAreCaseAndSpaceInsensitiveAndExtraColumnsIgnored() {
        CandleSeries s = TradingViewCsv.parse(" Time , OPEN,High ,low,Close, Volume ,RSI\n60,1,2,0.5,1.5,3,55\n");
        assertEquals(1.5, s.close(0));
        assertEquals(3.0, s.volume(0));
    }

    @Test
    void duplicatesKeepLastThenSortThenDropIncompleteRows() {
        CandleSeries s = TradingViewCsv.parse("time,open,high,low,close,volume\n"
                + "600,1,2,0.5,1.5,1\n"
                + "300,1,2,0.5,1.1,1\n"
                + "300,1,2,0.5,1.2,1\n"   // doublon : la dernière ligne gagne
                + "900,1,2,0.5,,1\n"      // close vide : ignorée
                + "\n");
        assertEquals(2, s.size());
        assertEquals(Instant.ofEpochSecond(300), s.time(0));
        assertEquals(1.2, s.close(0));
        assertEquals(Instant.ofEpochSecond(600), s.time(1));
    }

    @Test
    void missingColumnsAreReported() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TradingViewCsv.parse("time,open,high,low\n1,1,1,1\n"));
        assertTrue(e.getMessage().contains("close"));
        assertThrows(IllegalArgumentException.class, () -> TradingViewCsv.parse("date,open,high,low,close\n"));
    }

    @Test
    void barDurationTakesSmallestOnTie() {
        CandleSeries s = TradingViewCsv.parse("time,open,high,low,close\n0,1,1,1,1\n600,1,1,1,1\n900,1,1,1,1\n");
        assertEquals(Duration.ofMinutes(5), s.barDuration());
    }
}
