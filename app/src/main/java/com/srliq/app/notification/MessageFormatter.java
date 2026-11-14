package com.srliq.app.notification;

import com.srliq.app.news.NewsEvent;
import com.srliq.app.paper.PaperTrade;
import com.srliq.app.signal.SignalRecord;
import com.srliq.app.signal.SignalSource;
import com.srliq.app.trading.TradingHalt;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;
import com.srliq.engine.strategy.TrendMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Textes des notifications, en français, en texte brut (dans l'esprit de {@code message.py} de la
 * référence). Aucune promesse de gain : chaque message rappelle qu'il s'agit d'une information
 * automatique et, pour le paper trading, qu'aucun ordre réel n'est passé.
 */
public final class MessageFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    static final String DISCLAIMER = "Information automatique, pas un conseil financier : aucun résultat n'est garanti.";

    private MessageFormatter() {
    }

    /**
     * @param instrumentName nom de l'instrument, ou {@code null}
     * @param trendMode      filtre de tendance du moteur (signaux ENGINE), ou {@code null}
     * @param upcomingNews   annonces à fort impact des 24 h suivant le signal
     * @param activeHalt     arrêt du coupe-circuit en cours, s'il y en a un
     */
    public static String signal(SignalRecord s, String instrumentName, TrendMode trendMode,
                                List<NewsEvent> upcomingNews, Optional<TradingHalt> activeHalt) {
        List<String> lines = new ArrayList<>();
        lines.add("SIGNAL " + s.symbol() + " - " + side(s.direction()));
        lines.add("Instrument : " + s.symbol() + (instrumentName == null ? "" : " (" + instrumentName + ")"));
        lines.add("Source : " + (s.source() == SignalSource.ENGINE ? "moteur SR-Liq" : "alerte TradingView")
                + ", timeframe " + s.timeframe());
        lines.add("Émis : " + DATE_TIME.format(s.time()) + " UTC"
                + (s.session() == null ? "" : " (session " + s.session() + ")"));
        lines.add("Entrée réf. : " + price(s.entry()));
        lines.add("Stop loss   : " + price(s.stop()) + "  (risque " + price(Math.abs(s.entry() - s.stop())) + ")");
        lines.add("Objectif    : " + price(s.target()) + "  (R:R 1:" + plain(s.rr()) + ")");
        if (s.nextZone() != null) {
            lines.add("Prochaine zone opposée : " + price(s.nextZone()));
            double lo = Math.min(s.entry(), s.target());
            double hi = Math.max(s.entry(), s.target());
            if (lo < s.nextZone() && s.nextZone() < hi) {
                lines.add("Attention : une zone opposée se trouve AVANT l'objectif, prise partielle à envisager.");
            }
        }
        lines.add("");
        lines.add("Raison :");
        if (s.zone() != null) {
            lines.add("- Zone S/R : " + s.zone());
        }
        if (s.contacts() != null) {
            lines.add("- Contacts de la zone : " + s.contacts());
        }
        if (s.sweepTime() != null && s.sweepExtreme() != null) {
            lines.add("- Liquidation : mèche à " + price(s.sweepExtreme()) + " (" + TIME.format(s.sweepTime())
                    + " UTC) puis réintégration");
        }
        if (s.source() == SignalSource.ENGINE) {
            lines.add("- Entrée : cassure de structure LTF (CHOCH) en clôture");
        }
        if (s.source() == SignalSource.ENGINE && trendMode != null && trendMode != TrendMode.OFF) {
            // Le moteur n'émet un achat qu'en tendance HTF haussière (vente : baissière) quand le filtre est actif.
            lines.add("- Tendance H4 : " + (s.direction() == Direction.LONG ? "haussière" : "baissière")
                    + " (filtre « " + trendMode.pyName() + " »)");
        }
        lines.add("- Biais fondamental : " + (s.bias() == null ? "non renseigné" : s.bias()));
        lines.add("");
        if (upcomingNews.isEmpty()) {
            lines.add("Aucune annonce à fort impact dans les 24 h.");
        } else {
            lines.add("Annonces à fort impact dans les 24 h :");
            for (NewsEvent n : upcomingNews) {
                lines.add("- " + DATE_TIME.format(n.time()) + " UTC " + n.currency() + " " + n.event());
            }
        }
        lines.add("");
        lines.add(haltLine(activeHalt));
        lines.add("");
        lines.add("Invalidation : clôture au-delà du stop. " + DISCLAIMER);
        return String.join("\n", lines);
    }

    public static String tradeClosed(PaperTrade t) {
        List<String> lines = new ArrayList<>();
        lines.add("TRADE SIMULÉ CLÔTURÉ - " + t.symbol() + " " + t.timeframe() + " - " + side(t.direction()));
        lines.add("Signal : " + DATE_TIME.format(t.signalTime()) + " UTC");
        lines.add("Entrée : " + price(t.entry()) + " (" + DATE_TIME.format(t.entryTime()) + " UTC)");
        lines.add("Sortie : " + price(t.exitPrice()) + " (" + DATE_TIME.format(t.exitTime()) + " UTC, "
                + outcome(t.outcome()) + ")");
        lines.add("Résultat : " + signed(t.r()) + " R (coût déduit)");
        if (t.quantity() > 0) {
            lines.add("Taille simulée : " + t.quantity() + " contrat(s), résultat " + signed(t.pnlCurrency()) + " $");
        } else {
            lines.add("Taille simulée : 0 contrat (risque d'un contrat supérieur au budget de risque)");
        }
        lines.add("");
        lines.add("Paper trading : aucun ordre réel n'a été passé. " + DISCLAIMER);
        return String.join("\n", lines);
    }

    public static String haltChanged(TradingHalt h, boolean halted) {
        if (halted) {
            return String.join("\n",
                    "COUPE-CIRCUIT ACTIVÉ - " + DATE_TIME.format(h.startedAt()) + " UTC",
                    "Motif : " + h.reason(),
                    "Aucune nouvelle entrée simulée tant qu'il est actif ; les trades déjà ouverts sont gérés"
                            + " jusqu'à leur sortie.");
        }
        return String.join("\n",
                "COUPE-CIRCUIT DÉSACTIVÉ - " + DATE_TIME.format(h.endedAt()) + " UTC",
                "Arrêt du " + DATE_TIME.format(h.startedAt()) + " UTC au " + DATE_TIME.format(h.endedAt())
                        + " UTC (motif : " + h.reason() + ")",
                "Reprise des entrées simulées.");
    }

    static String haltLine(Optional<TradingHalt> activeHalt) {
        return activeHalt
                .map(h -> "Coupe-circuit : ACTIF depuis " + DATE_TIME.format(h.startedAt()) + " UTC (" + h.reason()
                        + ") : aucune nouvelle entrée simulée.")
                .orElse("Coupe-circuit : inactif.");
    }

    private static String side(Direction d) {
        return d == Direction.LONG ? "ACHAT (long)" : "VENTE (short)";
    }

    private static String outcome(Outcome o) {
        return switch (o) {
            case TP -> "objectif atteint";
            case SL -> "stop touché";
            case EOD -> "clôture de fin de journée";
            case TIMEOUT -> "durée maximale atteinte";
        };
    }

    static String price(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String signed(double v) {
        return String.format(Locale.ROOT, "%+.2f", v);
    }

    /** Comme {@code :g} en Python pour un R:R (2 -> "2", 2.5 -> "2.5"). */
    static String plain(double v) {
        if (!Double.isFinite(v)) {
            return String.valueOf(v);
        }
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    static String dateTime(Instant t) {
        return DATE_TIME.format(t);
    }
}
