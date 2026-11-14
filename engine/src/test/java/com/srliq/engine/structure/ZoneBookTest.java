package com.srliq.engine.structure;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneBookTest {

    private static final Instant T0 = Instant.parse("2026-01-05T00:00:00Z");

    private static Pivot p(PivotKind k, double price, int hours) {
        Instant t = T0.plus(Duration.ofHours(hours));
        return new Pivot(k, price, t, t.plus(Duration.ofHours(6)));
    }

    @Test
    void contactCountedEvenWhenWideningWouldExceedMaxWidth() {
        ZoneBook book = new ZoneBook(Duration.ofHours(300), 1.0);
        book.add(p(PivotKind.HIGH, 100.0, 0), 2.0);
        book.add(p(PivotKind.LOW, 101.5, 1), 2.0); // dans la tolérance, mais largeur 1.5 > 1.0
        assertEquals(1, book.zones().size());
        Zone z = book.zones().get(0);
        assertEquals(100.0, z.low());
        assertEquals(100.0, z.high());
        assertEquals(2, z.touches());
        assertEquals("100.00-100.00 (2 contacts, pivots high/low)", z.describe());
        assertEquals(T0.plus(Duration.ofHours(1)), z.lastTime());
    }

    @Test
    void nearestMidWinsAndFirstZoneWinsOnTie() {
        ZoneBook book = new ZoneBook(Duration.ofHours(300), 10.0);
        book.add(p(PivotKind.HIGH, 100.0, 0), 0.1);
        book.add(p(PivotKind.HIGH, 102.0, 1), 0.1);
        assertEquals(2, book.zones().size());
        book.add(p(PivotKind.LOW, 101.0, 2), 1.0); // à égale distance des deux milieux
        assertEquals(2, book.zones().get(0).touches());
        assertEquals(1, book.zones().get(1).touches());
        assertEquals(1, book.active(2).size());
    }

    @Test
    void pruneKeepsZonesExactlyAtMaxAge() {
        ZoneBook book = new ZoneBook(Duration.ofHours(10), null);
        book.add(p(PivotKind.HIGH, 100.0, 0), 0.1);
        book.prune(T0.plus(Duration.ofHours(10)));
        assertEquals(1, book.zones().size());
        book.prune(T0.plus(Duration.ofHours(10)).plusSeconds(1));
        assertEquals(0, book.zones().size());
    }
}
