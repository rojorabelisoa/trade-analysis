package com.srliq.engine.strategy;

import java.util.Locale;

/** Sens autorisés. Accepte les chaînes de la référence Python. */
public enum DirectionFilter {
    BOTH("both"),
    LONG("long"),
    SHORT("short");

    private final String pyName;

    DirectionFilter(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    /** Valeur de la référence Python (insensible à la casse et aux espaces). */
    public static DirectionFilter fromPython(String s) {
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (DirectionFilter m : values()) {
            if (m.pyName.equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("DirectionFilter inconnu : '" + s + "'");
    }

    @Override
    public String toString() {
        return pyName;
    }
}
