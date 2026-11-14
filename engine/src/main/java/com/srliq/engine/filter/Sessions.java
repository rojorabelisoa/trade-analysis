package com.srliq.engine.filter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sessions de marché en heures UTC ({@code SESSIONS} / {@code session_of}). */
public final class Sessions {

    /** Nom -> [heure de début incluse, heure de fin exclue). Ordre de la référence. */
    public static final Map<String, int[]> SESSIONS;

    static {
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("asie", new int[]{0, 7});
        m.put("londres", new int[]{7, 12});
        m.put("overlap_londres_ny", new int[]{12, 16});
        m.put("new_york", new int[]{16, 21});
        m.put("fermeture", new int[]{21, 24});
        SESSIONS = java.util.Collections.unmodifiableMap(m);
    }

    private Sessions() {
    }

    public static String sessionOf(Instant ts) {
        int h = ts.atZone(ZoneOffset.UTC).getHour();
        for (Map.Entry<String, int[]> e : SESSIONS.entrySet()) {
            if (e.getValue()[0] <= h && h < e.getValue()[1]) {
                return e.getKey();
            }
        }
        return "inconnue";
    }
}
