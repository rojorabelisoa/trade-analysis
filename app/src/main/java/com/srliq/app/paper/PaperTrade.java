package com.srliq.app.paper;

import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;

import java.time.Instant;

/**
 * Trade simulé (table {@code paper_trade}).
 *
 * @param status       OPEN (il manque des bougies pour le terminer) ou CLOSED
 * @param entry        ouverture de la bougie qui suit le signal
 * @param riskPoints   |entrée - stop|, dénominateur du R
 * @param exitTime     heure d'ouverture de la bougie de sortie ({@code null} si OPEN)
 * @param exitPrice    prix de sortie ({@code null} si OPEN)
 * @param outcome      type de sortie ({@code null} si OPEN)
 * @param lastTime     dernière bougie prise en compte (= sortie si CLOSED)
 * @param lastPrice    prix de sortie, ou dernière clôture si OPEN
 * @param r            résultat en R, coût déduit (latent si OPEN)
 * @param cost         coût par trade, en points de prix
 * @param quantity     contrats (0 si le risque d'un contrat dépasse le budget)
 * @param pnlCurrency  r x riskPoints x pointValue x quantity
 * @param riskTooLarge risque d'un contrat supérieur au budget de risque
 */
public record PaperTrade(
        long id,
        String symbol,
        String timeframe,
        long signalId,
        Instant signalTime,
        Direction direction,
        PaperTradeStatus status,
        Instant entryTime,
        double entry,
        double stop,
        double target,
        double riskPoints,
        Instant exitTime,
        Double exitPrice,
        Outcome outcome,
        Instant lastTime,
        double lastPrice,
        double r,
        double cost,
        int quantity,
        double pointValue,
        double pnlCurrency,
        boolean riskTooLarge,
        String session,
        String bias,
        Instant createdAt,
        Instant updatedAt) {
}
