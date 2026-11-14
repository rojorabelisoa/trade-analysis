package com.srliq.app.webhook;

import com.srliq.engine.filter.BiasBook;
import com.srliq.engine.filter.Sessions;
import com.srliq.engine.model.Direction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validation stricte d'une alerte TradingView. Liste vide = alerte valide. */
public final class TradingViewAlertValidator {

    /** Premier segment de "tf" = {@code timeframe.period} Pine : "5", "15", "60", "1D", "30S"… */
    private static final Pattern PINE_PERIOD = Pattern.compile("(\\d*)([SDWM]?)");

    private TradingViewAlertValidator() {
    }

    public static List<String> validate(TradingViewAlert a) {
        List<String> errors = new ArrayList<>();
        text(errors, "symbol", a.symbol(), 32);
        Direction dir = null;
        if (a.direction() == null) {
            errors.add("direction : obligatoire");
        } else if (a.direction().equals("long")) {
            dir = Direction.LONG;
        } else if (a.direction().equals("short")) {
            dir = Direction.SHORT;
        } else {
            errors.add("direction : doit valoir \"long\" ou \"short\"");
        }
        boolean prices = price(errors, "entry", a.entry()) & price(errors, "stop", a.stop())
                & price(errors, "target", a.target());
        if (prices && dir != null) {
            double e = a.entry();
            if (dir == Direction.LONG) {
                if (!(a.stop() < e)) {
                    errors.add("stop : doit être sous l'entrée pour un long");
                }
                if (!(a.target() > e)) {
                    errors.add("target : doit être au-dessus de l'entrée pour un long");
                }
            } else {
                if (!(a.stop() > e)) {
                    errors.add("stop : doit être au-dessus de l'entrée pour un short");
                }
                if (!(a.target() < e)) {
                    errors.add("target : doit être sous l'entrée pour un short");
                }
            }
        }
        if (price(errors, "rr", a.rr()) && !(a.rr() > 0)) {
            errors.add("rr : doit être strictement positif");
        }
        price(errors, "sweep", a.sweep());
        text(errors, "zone", a.zone(), 200);
        if (a.contacts() == null) {
            errors.add("contacts : obligatoire");
        } else if (a.contacts() < 1) {
            errors.add("contacts : doit être >= 1");
        }
        if (a.bias() == null) {
            errors.add("bias : obligatoire");
        } else if (!BiasBook.VALID_BIAS.contains(a.bias())) {
            errors.add("bias : doit valoir " + BiasBook.VALID_BIAS);
        }
        if (a.session() == null) {
            errors.add("session : obligatoire");
        } else if (!Sessions.SESSIONS.containsKey(a.session())) {
            errors.add("session : doit valoir " + Sessions.SESSIONS.keySet());
        }
        if (text(errors, "tf", a.tf(), 32) && entryTimeframe(a.tf()).isEmpty()) {
            errors.add("tf : timeframe d'entrée illisible (attendu par ex. \"5/60/240\")");
        }
        return errors;
    }

    /** Durée de la bougie d'entrée lue dans "tf" ("5/60/240" -> 5 min). */
    public static Optional<Duration> entryTimeframe(String tf) {
        if (tf == null) {
            return Optional.empty();
        }
        String first = tf.split("/", -1)[0].strip().toUpperCase(Locale.ROOT);
        Matcher m = PINE_PERIOD.matcher(first);
        if (!m.matches() || (m.group(1).isEmpty() && m.group(2).isEmpty())) {
            return Optional.empty();
        }
        long n;
        try {
            n = m.group(1).isEmpty() ? 1 : Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (n <= 0 || n > 100_000) {
            return Optional.empty();
        }
        return switch (m.group(2)) {
            case "" -> Optional.of(Duration.ofMinutes(n));
            case "S" -> Optional.of(Duration.ofSeconds(n));
            case "D" -> Optional.of(Duration.ofDays(n));
            default -> Optional.empty(); // semaines / mois : pas un timeframe d'entrée intraday
        };
    }

    /** Nombre fini (un prix peut être négatif : pétrole en avril 2020). */
    private static boolean price(List<String> errors, String field, Double v) {
        if (v == null) {
            errors.add(field + " : obligatoire");
            return false;
        }
        if (!Double.isFinite(v)) {
            errors.add(field + " : doit être un nombre fini");
            return false;
        }
        return true;
    }

    private static boolean text(List<String> errors, String field, String v, int max) {
        if (v == null || v.isBlank()) {
            errors.add(field + " : obligatoire");
            return false;
        }
        if (v.length() > max) {
            errors.add(field + " : " + max + " caractères au plus");
            return false;
        }
        return true;
    }
}
