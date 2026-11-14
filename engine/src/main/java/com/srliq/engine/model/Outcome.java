package com.srliq.engine.model;

/** Type de sortie d'un trade : objectif, stop, durée maximale, fin de journée. */
public enum Outcome {
    TP("tp"),
    SL("sl"),
    TIMEOUT("timeout"),
    EOD("eod");

    private final String pyName;

    Outcome(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    public static Outcome fromPython(String s) {
        for (Outcome o : values()) {
            if (o.pyName.equalsIgnoreCase(s.strip())) {
                return o;
            }
        }
        throw new IllegalArgumentException("Sortie inconnue : " + s);
    }

    @Override
    public String toString() {
        return pyName;
    }
}
