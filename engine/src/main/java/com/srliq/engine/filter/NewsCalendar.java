package com.srliq.engine.filter;

import com.srliq.engine.data.CsvTable;
import com.srliq.engine.data.TimeParser;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Annonces économiques ({@code NewsCalendar}). Seules les annonces d'impact retenu
 * ("high" par défaut) bloquent ; fenêtre {@code [t - before, t + after]} fermée des deux côtés.
 */
public final class NewsCalendar {

    /** Ligne de {@code news.csv} (heure en UTC). */
    public record Event(Instant time, String currency, String impact, String event) {
    }

    private static final DateTimeFormatter UPCOMING = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private final Duration before;
    private final Duration after;
    private final List<Instant> times;
    private final List<String> events;

    private NewsCalendar(Duration before, Duration after, List<Instant> times, List<String> events) {
        this.before = before;
        this.after = after;
        this.times = times;
        this.events = events;
    }

    public static NewsCalendar none() {
        return new NewsCalendar(Duration.ZERO, Duration.ZERO, List.of(), List.of());
    }

    public static NewsCalendar of(List<Event> rows, Duration before, Duration after) {
        return of(rows, before, after, Set.of("high"));
    }

    public static NewsCalendar of(List<Event> rows, Duration before, Duration after, Set<String> impacts) {
        if (rows == null || rows.isEmpty()) {
            return new NewsCalendar(before, after, List.of(), List.of());
        }
        List<Event> kept = new ArrayList<>();
        for (Event e : rows) {
            if (impacts.contains(e.impact().strip().toLowerCase(Locale.ROOT))) {
                kept.add(e);
            }
        }
        kept.sort(Comparator.comparing(Event::time)); // tri stable
        List<Instant> t = new ArrayList<>();
        List<String> ev = new ArrayList<>();
        for (Event e : kept) {
            t.add(e.time());
            ev.add(e.event());
        }
        return new NewsCalendar(before, after, List.copyOf(t), List.copyOf(ev));
    }

    /** {@code NewsCalendar.from_csv} : colonnes time_utc, currency, impact, event. */
    public static NewsCalendar fromCsv(Path path, int beforeMin, int afterMin) {
        Duration b = Duration.ofMinutes(beforeMin);
        Duration a = Duration.ofMinutes(afterMin);
        if (path == null) {
            return new NewsCalendar(b, a, List.of(), List.of());
        }
        return fromTable(CsvTable.read(path), b, a);
    }

    public static NewsCalendar fromTable(CsvTable t, Duration before, Duration after) {
        List<Event> rows = new ArrayList<>();
        boolean hasCurrency = t.hasColumn("currency");
        for (int r = 0; r < t.size(); r++) {
            String impact = t.get(r, "impact");
            Instant time = TimeParser.parse(t.get(r, "time_utc"));
            String ev = t.get(r, "event");
            rows.add(new Event(time, hasCurrency ? t.get(r, "currency") : "", impact, ev.isEmpty() ? "nan" : ev));
        }
        return of(rows, before, after);
    }

    /** Nom de l'annonce si {@code ts} tombe dans une fenêtre d'exclusion. */
    public Optional<String> blocked(Instant ts) {
        for (int i = 0; i < times.size(); i++) {
            Instant t = times.get(i);
            if (!ts.isBefore(t.minus(before)) && !ts.isAfter(t.plus(after))) {
                return Optional.of(events.get(i));
            }
        }
        return Optional.empty();
    }

    /** Annonces dans {@code [ts, ts + horizon]} : "yyyy-MM-dd HH:mm UTC événement". */
    public List<String> upcoming(Instant ts, Duration horizon) {
        List<String> out = new ArrayList<>();
        Instant end = ts.plus(horizon);
        for (int i = 0; i < times.size(); i++) {
            Instant t = times.get(i);
            if (!t.isBefore(ts) && !t.isAfter(end)) {
                out.add(UPCOMING.format(t) + " UTC " + events.get(i));
            }
        }
        return out;
    }
}
