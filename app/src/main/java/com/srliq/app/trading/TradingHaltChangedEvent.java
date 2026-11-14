package com.srliq.app.trading;

/**
 * Publié dans la transaction qui active ({@code halted == true}) ou désactive le coupe-circuit.
 * Les écouteurs qui notifient utilisent {@code @TransactionalEventListener} (après commit).
 */
public record TradingHaltChangedEvent(TradingHalt halt, boolean halted) {
}
