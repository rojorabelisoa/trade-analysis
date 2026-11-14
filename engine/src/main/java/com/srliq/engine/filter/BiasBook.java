package com.srliq.engine.filter;

import com.srliq.engine.data.CsvTable;
import com.srliq.engine.data.TimeParser;
import com.srliq.engine.model.Direction;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Biais fondamental ({@code BiasBook}). Le biais s'applique à partir de sa date (minuit UTC)
 * jusqu'à la ligne suivante. Sans fichier : "neutral" ; avant la première date : "off".
 */
public final class BiasBook {

    public static final Set<String> VALID_BIAS = Set.of("long", "short", "neutral", "off");

    /** Ligne de {@code bias.csv}. */
    public record Entry(Instant date, String symbol, String bias) {
    }

    private final String symbol;
    /** null = pas de fichier ; vide = aucune ligne pour ce symbole (toujours "off"). */
    private final long[] dates;
    private final String[] biases;

    private BiasBook(String symbol, long[] dates, String[] biases) {
        this.symbol = symbol;
        this.dates = dates;
        this.biases = biases;
    }

    /** Aucun fichier de biais : toujours "neutral". */
    public static BiasBook none(String symbol) {
        return new BiasBook(symbol, null, null);
    }

    /**
     * @param entries lignes du fichier (tous symboles) ; {@code null} ou vide = pas de fichier
     */
    public static BiasBook of(List<Entry> entries, String symbol) {
        if (entries == null || entries.isEmpty()) {
            return none(symbol);
        }
        List<Entry> kept = new ArrayList<>();
        Set<String> bad = new TreeSet<>();
        for (Entry e : entries) {
            if (!String.valueOf(e.symbol()).toUpperCase(Locale.ROOT).equals(symbol.toUpperCase(Locale.ROOT))) {
                continue;
            }
            String b = e.bias().strip().toLowerCase(Locale.ROOT);
            if (!VALID_BIAS.contains(b)) {
                bad.add(b);
            }
            kept.add(new Entry(e.date(), e.symbol(), b));
        }
        if (!bad.isEmpty()) {
            throw new IllegalArgumentException("Biais inconnus dans bias.csv: " + bad + " (attendu: " + VALID_BIAS + ")");
        }
        kept.sort(Comparator.comparing(Entry::date)); // tri stable
        long[] d = new long[kept.size()];
        String[] b = new String[kept.size()];
        for (int i = 0; i < kept.size(); i++) {
            d[i] = kept.get(i).date().toEpochMilli();
            b[i] = kept.get(i).bias();
        }
        return new BiasBook(symbol, d, b);
    }

    /** {@code BiasBook.from_csv} : colonnes date, symbol, bias (note facultative). */
    public static BiasBook fromCsv(Path path, String symbol) {
        if (path == null) {
            return none(symbol);
        }
        return fromTable(CsvTable.read(path), symbol);
    }

    public static BiasBook fromTable(CsvTable t, String symbol) {
        if (t.isEmpty()) {
            return none(symbol);
        }
        List<Entry> entries = new ArrayList<>();
        for (int r = 0; r < t.size(); r++) {
            entries.add(new Entry(TimeParser.parse(t.get(r, "date")), t.get(r, "symbol"), t.get(r, "bias")));
        }
        return of(entries, symbol);
    }

    public String symbol() {
        return symbol;
    }

    /** Biais en vigueur à l'instant {@code ts}. */
    public String at(Instant ts) {
        if (dates == null) {
            return "neutral";
        }
        int k = com.srliq.engine.util.PyMath.searchRight(dates, ts.toEpochMilli()) - 1;
        return k >= 0 ? biases[k] : "off";
    }

    public boolean allows(Instant ts, Direction direction, boolean neutralAllowsBoth) {
        String b = at(ts);
        if (b.equals("off")) {
            return false;
        }
        if (b.equals("neutral")) {
            return neutralAllowsBoth;
        }
        return b.equals(direction.pyName());
    }
}
