package com.srliq.app.broker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Garde-fou de l'argent réel. {@code trading.live.enabled} vaut {@code false} par défaut ; s'il vaut
 * {@code true}, l'application refuse de démarrer : aucun adaptateur de courtier réel n'existe, et
 * aucun ordre réel ne doit pouvoir partir par erreur.
 */
@Component
public class LiveTradingGuard {

    static final String MESSAGE = "trading.live.enabled=true est refusé : aucun adaptateur de courtier réel "
            + "n'est disponible dans cette version (seul le paper trading existe). Retirez cette propriété "
            + "(ou mettez-la à false) pour démarrer l'application.";

    private final boolean liveEnabled;

    public LiveTradingGuard(@Value("${trading.live.enabled:false}") boolean liveEnabled) {
        if (liveEnabled) {
            throw new IllegalStateException(MESSAGE);
        }
        this.liveEnabled = false;
    }

    /** Toujours {@code false} : une instance n'existe que si l'exécution réelle est désactivée. */
    public boolean liveEnabled() {
        return liveEnabled;
    }
}
