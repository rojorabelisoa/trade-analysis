package com.srliq.engine.structure;

import com.srliq.engine.util.PyMath;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Zone S/R construite à partir de pivots HTF (mutable, comme la dataclass Python). */
public final class Zone {

    private double low;
    private double high;
    private int touches;
    private final Instant firstTime;
    private Instant lastTime;
    private final TreeSet<String> kinds = new TreeSet<>();

    public Zone(double low, double high, int touches, Instant firstTime, Instant lastTime, Set<String> kinds) {
        this.low = low;
        this.high = high;
        this.touches = touches;
        this.firstTime = firstTime;
        this.lastTime = lastTime;
        this.kinds.addAll(kinds);
    }

    public double low() {
        return low;
    }

    public double high() {
        return high;
    }

    public int touches() {
        return touches;
    }

    public Instant firstTime() {
        return firstTime;
    }

    public Instant lastTime() {
        return lastTime;
    }

    public Set<String> kinds() {
        return Collections.unmodifiableSet(kinds);
    }

    public double mid() {
        return (low + high) / 2;
    }

    /** {@code "1995.41-1996.36 (2 contacts, pivots high/low)"} (format {@code %.2f} Python). */
    public String describe() {
        return PyMath.format2(low) + "-" + PyMath.format2(high) + " (" + touches + " contacts, pivots "
                + String.join("/", kinds) + ")";
    }

    void absorb(Pivot p, Double maxWidth) {
        double newLow = Math.min(low, p.price());
        double newHigh = Math.max(high, p.price());
        if (maxWidth == null || newHigh - newLow <= maxWidth) {
            low = newLow;
            high = newHigh;
        }
        touches += 1;
        if (p.time().isAfter(lastTime)) {
            lastTime = p.time();
        }
        kinds.add(p.kind().pyName());
    }

    @Override
    public String toString() {
        return describe();
    }
}
