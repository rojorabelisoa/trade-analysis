package com.srliq.app.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Adaptateur « papier » : journalise les ordres, n'envoie rien à personne, ne tient aucune position
 * (les positions simulées sont celles du paper trading, table {@code paper_trade}).
 */
@Component
public class PaperBrokerAdapter implements BrokerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PaperBrokerAdapter.class);

    @Override
    public String id() {
        return "paper";
    }

    @Override
    public OrderAck submitEntry(EntryOrder order) {
        String id = "paper-" + UUID.randomUUID();
        log.info("[PAPIER] ordre {} {} x{} {} (limite {}) stop {} objectif {} -> {} (aucun envoi)", order.symbol(),
                order.direction(), order.quantity(), order.clientOrderId(),
                order.limitPrice() == null ? "marché" : order.limitPrice(), order.stop(), order.target(), id);
        return new OrderAck(id, order.clientOrderId(), true, "ordre simulé, rien n'a été envoyé");
    }

    @Override
    public void cancel(String orderId) {
        log.info("[PAPIER] annulation de {} (aucun envoi)", orderId);
    }

    @Override
    public List<Position> positions() {
        return List.of();
    }
}
