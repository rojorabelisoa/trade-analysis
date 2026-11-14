package com.srliq.app.marketdata;

/**
 * Source de bougies de marché. Une source POUSSE des lots de bougies vers un {@link CandleSink} :
 * <ul>
 *   <li>à la demande (import d'un fichier : {@link TradingViewCsvSource}) ;</li>
 *   <li>en continu pour un futur flux temps réel, entre {@link #start(CandleSink)} et {@link #stop()}.</li>
 * </ul>
 * Contrat : seules des bougies <b>clôturées</b> sont transmises (le puits écarte de toute façon
 * celles dont la clôture est dans le futur), heures d'ouverture en UTC.
 */
public interface MarketDataSource {

    /** Identifiant court et stable de la source. */
    String id();

    /** Démarre l'envoi continu vers {@code sink}. Rien à faire pour une source à la demande. */
    default void start(CandleSink sink) {
    }

    default void stop() {
    }
}
