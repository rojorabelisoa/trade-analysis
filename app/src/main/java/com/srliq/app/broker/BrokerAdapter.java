package com.srliq.app.broker;

import com.srliq.engine.model.Direction;

import java.util.List;
import java.util.Objects;

/**
 * Exécution chez un courtier (phase 7 : interface seulement). Le seul adaptateur existant est
 * {@link PaperBrokerAdapter}, qui ne fait que journaliser. Aucun adaptateur réel n'est fourni :
 * {@link LiveTradingGuard} empêche le démarrage si {@code trading.live.enabled=true}.
 */
public interface BrokerAdapter {

    /** Identifiant de l'adaptateur ("paper"). */
    String id();

    /** Soumet un ordre d'entrée avec son stop et son objectif (ordre groupé « bracket »). */
    OrderAck submitEntry(EntryOrder order);

    /** Annule un ordre non exécuté. */
    void cancel(String orderId);

    /** Positions ouvertes. */
    List<Position> positions();

    /**
     * Ordre d'entrée.
     *
     * @param clientOrderId identifiant choisi par l'application (idempotence côté courtier)
     * @param limitPrice    prix limite, ou {@code null} pour un ordre au marché
     */
    record EntryOrder(String clientOrderId, String symbol, Direction direction, int quantity, Double limitPrice,
                      double stop, double target) {
        public EntryOrder {
            Objects.requireNonNull(clientOrderId, "clientOrderId");
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(direction, "direction");
            if (quantity <= 0) {
                throw new IllegalArgumentException("La quantité doit être > 0");
            }
        }
    }

    /** Accusé de réception d'un ordre. */
    record OrderAck(String orderId, String clientOrderId, boolean accepted, String message) {
    }

    /** Position ouverte : quantité signée (> 0 acheteur, < 0 vendeur). */
    record Position(String symbol, int quantity, double averagePrice) {
    }
}
