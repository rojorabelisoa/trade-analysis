package com.srliq.engine.model;

/** Sens d'un signal. {@link #pyName()} = "long" / "short" comme dans la référence. */
public enum Direction {
    LONG(1, "long"),
    SHORT(-1, "short");

    private final int sign;
    private final String pyName;

    Direction(int sign, String pyName) {
        this.sign = sign;
        this.pyName = pyName;
    }

    /** +1 pour un achat, -1 pour une vente. */
    public int sign() {
        return sign;
    }

    public String pyName() {
        return pyName;
    }

    public static Direction fromPython(String s) {
        for (Direction d : values()) {
            if (d.pyName.equalsIgnoreCase(s.strip())) {
                return d;
            }
        }
        throw new IllegalArgumentException("Sens inconnu : " + s);
    }

    @Override
    public String toString() {
        return pyName;
    }
}
