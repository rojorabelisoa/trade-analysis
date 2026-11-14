package com.srliq.app.signal;

/**
 * Publié (événement Spring) quand un NOUVEAU signal est enregistré ; jamais pour un doublon.
 * Point d'extension pour le paper trading et Telegram.
 *
 * <p>Publié dans la transaction d'enregistrement : un écouteur qui ne doit agir qu'une fois
 * le signal validé en base utilisera {@code @TransactionalEventListener} (après commit).
 * Attention : un premier import d'historique enregistre tous les signaux passés ; un écouteur
 * « temps réel » filtrera sur {@link SignalRecord#time()} récent.
 */
public record SignalRecordedEvent(SignalRecord signal) {
}
