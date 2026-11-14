package com.srliq.app.trading;

import com.srliq.app.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Coupe-circuit global (tous instruments). Pendant une période d'arrêt, aucun signal n'ouvre de
 * trade simulé ; les trades déjà ouverts continuent d'être gérés jusqu'à leur sortie. Le rejeu du
 * paper trading lit les périodes d'arrêt : l'effet est donc le même à chaque rejeu.
 */
@Service
public class TradingHaltService {

    private static final Logger log = LoggerFactory.getLogger(TradingHaltService.class);

    private final TradingHaltRepository halts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TradingHaltService(TradingHaltRepository halts, ApplicationEventPublisher events, Clock clock) {
        this.halts = halts;
        this.events = events;
        this.clock = clock;
    }

    /** Ouvre une période d'arrêt maintenant ; 409 si le coupe-circuit est déjà actif. */
    @Transactional
    public TradingHalt halt(String reason) {
        if (halts.findActive().isPresent()) {
            throw ApiException.conflict("Le coupe-circuit est déjà actif");
        }
        TradingHalt h;
        try {
            h = halts.open(clock.instant(), reason.strip());
        } catch (DuplicateKeyException e) { // activation concurrente
            throw ApiException.conflict("Le coupe-circuit est déjà actif");
        }
        log.warn("Coupe-circuit ACTIVÉ ({}) : aucune nouvelle entrée simulée", h.reason());
        events.publishEvent(new TradingHaltChangedEvent(h, true));
        return h;
    }

    /** Ferme la période d'arrêt en cours ; 409 si le coupe-circuit n'est pas actif. */
    @Transactional
    public TradingHalt resume() {
        TradingHalt h = halts.close(clock.instant())
                .orElseThrow(() -> ApiException.conflict("Le coupe-circuit n'est pas actif"));
        log.warn("Coupe-circuit DÉSACTIVÉ : reprise des entrées simulées");
        events.publishEvent(new TradingHaltChangedEvent(h, false));
        return h;
    }

    public Optional<TradingHalt> active() {
        return halts.findActive();
    }

    public List<TradingHalt> all() {
        return halts.findAll();
    }

    public List<TradingHalt> recent(int limit) {
        return halts.recent(limit);
    }

    /** Vrai si {@code t} tombe dans une des périodes. */
    public static boolean halted(List<TradingHalt> periods, Instant t) {
        for (TradingHalt h : periods) {
            if (h.covers(t)) {
                return true;
            }
        }
        return false;
    }
}
