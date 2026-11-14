package com.srliq.app.marketdata;

import com.srliq.app.signal.RecomputeResult;

/**
 * Résultat de l'ingestion d'un lot.
 *
 * @param received    bougies reçues
 * @param skippedOpen bougies écartées car pas encore clôturées
 * @param inserted    nouvelles bougies
 * @param updated     bougies existantes modifiées (valeurs différentes)
 * @param unchanged   bougies déjà présentes à l'identique
 * @param signals     recalcul des signaux déclenché après l'ingestion
 */
public record IngestResult(String symbol, String timeframe, int received, int skippedOpen, int inserted,
                           int updated, int unchanged, RecomputeResult signals) {
}
