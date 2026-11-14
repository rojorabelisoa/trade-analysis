package com.srliq.engine.filter;

import com.srliq.engine.data.CsvTable;
import com.srliq.engine.model.Direction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltersTest {

    private static final String BIAS = "date,symbol,bias,note\n"
            + "2026-01-05,GC,neutral,pas de conviction\n"
            + "2026-01-12,GC, Long ,Fed dovish\n"
            + "2026-01-08,SI,short,autre symbole\n";

    @Test
    void biasWithoutFileIsNeutralAndBeforeFirstDateIsOff() {
        assertEquals("neutral", BiasBook.none("GC").at(Instant.parse("2020-01-01T00:00:00Z")));
        BiasBook b = BiasBook.fromTable(CsvTable.parse(BIAS), "gc");
        assertEquals("off", b.at(Instant.parse("2026-01-04T23:59:00Z")));
        assertEquals("neutral", b.at(Instant.parse("2026-01-05T00:00:00Z")));
        assertEquals("neutral", b.at(Instant.parse("2026-01-11T23:59:00Z")));
        assertEquals("long", b.at(Instant.parse("2026-01-12T00:00:00Z")));
        assertTrue(b.allows(Instant.parse("2026-01-13T00:00:00Z"), Direction.LONG, false));
        assertFalse(b.allows(Instant.parse("2026-01-13T00:00:00Z"), Direction.SHORT, true));
        assertFalse(b.allows(Instant.parse("2026-01-06T00:00:00Z"), Direction.SHORT, false));
        assertTrue(b.allows(Instant.parse("2026-01-06T00:00:00Z"), Direction.SHORT, true));
        assertEquals("off", BiasBook.fromTable(CsvTable.parse(BIAS), "HG").at(Instant.parse("2026-02-01T00:00:00Z")));
    }

    @Test
    void unknownBiasIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BiasBook.fromTable(CsvTable.parse("date,symbol,bias\n2026-01-05,GC,bullish\n"), "GC"));
    }

    @Test
    void newsOnlyHighImpactWithClosedWindow() {
        NewsCalendar n = NewsCalendar.fromTable(CsvTable.parse("time_utc,currency,impact,event\n"
                + "2026-01-09 13:30,USD,High,NFP\n2026-01-09 15:00,USD,medium,Autre\n"),
                Duration.ofMinutes(30), Duration.ofMinutes(30));
        assertEquals("NFP", n.blocked(Instant.parse("2026-01-09T13:00:00Z")).orElseThrow());
        assertEquals("NFP", n.blocked(Instant.parse("2026-01-09T14:00:00Z")).orElseThrow());
        assertTrue(n.blocked(Instant.parse("2026-01-09T14:00:01Z")).isEmpty());
        assertTrue(n.blocked(Instant.parse("2026-01-09T15:00:00Z")).isEmpty());
        assertEquals(List.of("2026-01-09 13:30 UTC NFP"),
                n.upcoming(Instant.parse("2026-01-09T00:00:00Z"), Duration.ofDays(1)));
    }

    @Test
    void sessionsByUtcHour() {
        assertEquals("asie", Sessions.sessionOf(Instant.parse("2026-01-05T06:59:00Z")));
        assertEquals("londres", Sessions.sessionOf(Instant.parse("2026-01-05T07:00:00Z")));
        assertEquals("overlap_londres_ny", Sessions.sessionOf(Instant.parse("2026-01-05T12:00:00Z")));
        assertEquals("new_york", Sessions.sessionOf(Instant.parse("2026-01-05T20:59:00Z")));
        assertEquals("fermeture", Sessions.sessionOf(Instant.parse("2026-01-05T21:00:00Z")));
    }
}
