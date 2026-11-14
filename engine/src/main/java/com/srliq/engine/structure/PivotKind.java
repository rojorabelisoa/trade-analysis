package com.srliq.engine.structure;

/** Sommet ("high") ou creux ("low"). */
public enum PivotKind {
    HIGH("high"),
    LOW("low");

    private final String pyName;

    PivotKind(String pyName) {
        this.pyName = pyName;
    }

    public String pyName() {
        return pyName;
    }

    @Override
    public String toString() {
        return pyName;
    }
}
