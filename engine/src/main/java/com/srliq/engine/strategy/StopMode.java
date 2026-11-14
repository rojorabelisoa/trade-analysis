package com.srliq.engine.strategy;

import java.util.Locale;

/** Placement du stop (mèche du sweep, ou dernier creux/sommet du TF de tendance). Accepte les chaînes de la référence Python. */
public enum StopMode {
    SWEEP("sweep"),
    H4("h4");

    private final String pyName;

    StopMode(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    /** Valeur de la référence Python (insensible à la casse et aux espaces). */
    public static StopMode fromPython(String s) {
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (StopMode m : values()) {
            if (m.pyName.equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("StopMode inconnu : '" + s + "'");
    }

    @Override
    public String toString() {
        return pyName;
    }
}
