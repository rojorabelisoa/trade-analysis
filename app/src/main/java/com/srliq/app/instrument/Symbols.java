package com.srliq.app.instrument;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalisation des symboles venant de TradingView ou de l'utilisateur. */
public final class Symbols {

    /** Suffixe de contrat continu TradingView : "1!", "2!"… */
    private static final Pattern CONTINUOUS = Pattern.compile("\\d+!$");

    private Symbols() {
    }

    /**
     * "GC1!" -> "GC", "COMEX:MGC1!" -> "MGC", " gc " -> "GC". Le préfixe de place
     * ("COMEX:") et le suffixe de contrat continu sont retirés ; les contrats datés
     * ("GCZ2026") ne sont pas reconnus et restent tels quels.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip().toUpperCase(Locale.ROOT);
        int colon = s.lastIndexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        return CONTINUOUS.matcher(s).replaceFirst("");
    }
}
