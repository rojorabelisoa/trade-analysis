package com.srliq.engine.strategy;

import java.util.Locale;

/** Objectif (order block opposé, liquidité du TF de tendance, ou R:R fixe). Accepte les chaînes de la référence Python. */
public enum TargetMode {
    OB("ob"),
    H4("h4"),
    RR("rr");

    private final String pyName;

    TargetMode(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    /** Valeur de la référence Python (insensible à la casse et aux espaces). */
    public static TargetMode fromPython(String s) {
        String v = s.strip().toLowerCase(Locale.ROOT);
        for (TargetMode m : values()) {
            if (m.pyName.equals(v)) {
                return m;
            }
        }
        throw new IllegalArgumentException("TargetMode inconnu : '" + s + "'");
    }

    @Override
    public String toString() {
        return pyName;
    }
}
