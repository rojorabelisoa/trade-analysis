package com.srliq.engine.strategy;

import java.util.Locale;

/** Mode de tendance HTF. Accepte les chaînes de la référence Python. */
public enum TrendMode {
    STRUCTURE("structure"),
    STRICT("strict"),
    EMA("ema"),
    OFF("off");

    private final String pyName;

    TrendMode(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    /** Valeur de la référence Python (insensible à la casse et aux espaces). */
    public static TrendMode fromPython(String s) {
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (TrendMode m : values()) {
            if (m.pyName.equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("TrendMode inconnu : '" + s + "'");
    }

    @Override
    public String toString() {
        return pyName;
    }
}
