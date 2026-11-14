package com.srliq.engine.data;

import com.srliq.engine.model.CandleSeries;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Chargement d'un export TradingView (équivalent de {@code load_tradingview_csv}).
 *
 * <ul>
 *   <li>colonnes insensibles à la casse et aux espaces ; {@code time}, {@code open}, {@code high},
 *       {@code low}, {@code close} obligatoires, {@code volume} facultative (absente : 0) ;</li>
 *   <li>{@code time} = heure d'ouverture, en secondes Unix si toute la colonne est numérique,
 *       sinon en ISO 8601 (convertie en UTC) ;</li>
 *   <li>doublons d'horodatage : on garde la dernière ligne ; tri chronologique ;</li>
 *   <li>lignes dont un prix OHLC est vide ensuite ignorées.</li>
 * </ul>
 */
public final class TradingViewCsv {

    private static final String[] REQUIRED = {"open", "high", "low", "close"};

    private TradingViewCsv() {
    }

    public static CandleSeries read(Path path) {
        return fromTable(CsvTable.read(path), path.toString());
    }

    public static CandleSeries parse(String text) {
        return fromTable(CsvTable.parse(text), "<texte>");
    }

    public static CandleSeries fromTable(CsvTable t, String name) {
        if (!t.hasColumn("time")) {
            throw new IllegalArgumentException(name + ": colonne 'time' absente (colonnes: " + t.columns() + ")");
        }
        List<String> missing = new ArrayList<>();
        for (String c : REQUIRED) {
            if (!t.hasColumn(c)) {
                missing.add(c);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(name + ": colonnes manquantes " + missing);
        }
        boolean hasVolume = t.hasColumn("volume");

        // pandas : colonne numérique si toutes les valeurs non vides sont des nombres.
        boolean numericTime = true;
        for (int r = 0; r < t.size(); r++) {
            String v = t.get(r, "time").strip();
            if (!v.isEmpty() && !isNumber(v)) {
                numericTime = false;
                break;
            }
        }

        // Doublons : la dernière ligne gagne ; TreeMap = tri par heure.
        TreeMap<Long, double[]> byTime = new TreeMap<>();
        for (int r = 0; r < t.size(); r++) {
            String tv = t.get(r, "time").strip();
            if (tv.isEmpty()) {
                continue; // heure absente : ligne inexploitable
            }
            long millis = numericTime ? secondsToMillis(tv) : TimeParser.parse(tv).toEpochMilli();
            double[] row = new double[5];
            for (int k = 0; k < 4; k++) {
                row[k] = parseNumber(t.get(r, REQUIRED[k]), name, r);
            }
            row[4] = hasVolume ? parseNumber(t.get(r, "volume"), name, r) : 0.0;
            byTime.put(millis, row);
        }

        List<Long> times = new ArrayList<>();
        List<double[]> rows = new ArrayList<>();
        byTime.forEach((k, row) -> {
            if (!Double.isNaN(row[0]) && !Double.isNaN(row[1]) && !Double.isNaN(row[2]) && !Double.isNaN(row[3])) {
                times.add(k);
                rows.add(row);
            }
        });
        int n = times.size();
        long[] tt = new long[n];
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] c = new double[n];
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            tt[i] = times.get(i);
            double[] row = rows.get(i);
            o[i] = row[0];
            h[i] = row[1];
            l[i] = row[2];
            c[i] = row[3];
            v[i] = row[4];
        }
        return CandleSeries.ofArrays(tt, o, h, l, c, v);
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return !s.endsWith("d") && !s.endsWith("D") && !s.endsWith("f") && !s.endsWith("F");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static long secondsToMillis(String s) {
        return new BigDecimal(s).movePointRight(3).setScale(0, java.math.RoundingMode.HALF_EVEN).longValueExact();
    }

    /** Cellule vide (ou "nan") : NaN, comme pandas. */
    private static double parseNumber(String raw, String name, int row) {
        String s = raw.strip();
        if (s.isEmpty() || s.equalsIgnoreCase("nan")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": valeur non numérique '" + raw + "' (ligne " + (row + 2) + ")");
        }
    }

    /** Pour les tests et outils : instant d'une ligne au format Unix ou ISO. */
    static Instant parseTime(String s) {
        String v = s.strip();
        return isNumber(v) ? Instant.ofEpochMilli(secondsToMillis(v)) : TimeParser.parse(v);
    }
}
