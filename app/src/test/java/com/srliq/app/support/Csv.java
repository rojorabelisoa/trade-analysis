package com.srliq.app.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Découpe d'un export CSV TradingView (première colonne : heure Unix en secondes). */
public final class Csv {

    private Csv() {
    }

    /** En-tête + lignes dont l'heure d'ouverture est strictement antérieure à {@code cut}. */
    public static byte[] before(byte[] csv, Instant cut) {
        List<String> lines = lines(csv);
        List<String> out = new ArrayList<>();
        out.add(lines.get(0));
        for (String l : lines.subList(1, lines.size())) {
            if (!l.isBlank() && Long.parseLong(l.split(",")[0]) < cut.getEpochSecond()) {
                out.add(l);
            }
        }
        return (String.join("\n", out) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** Clôture (5e colonne) de la bougie qui ouvre à {@code t}. */
    public static double close(byte[] csv, Instant t) {
        for (String l : lines(csv)) {
            String[] f = l.split(",");
            if (f[0].equals(Long.toString(t.getEpochSecond()))) {
                return Double.parseDouble(f[4]);
            }
        }
        throw new IllegalArgumentException("Pas de bougie à " + t);
    }

    private static List<String> lines(byte[] csv) {
        return List.of(new String(csv, StandardCharsets.UTF_8).split("\\R"));
    }
}
