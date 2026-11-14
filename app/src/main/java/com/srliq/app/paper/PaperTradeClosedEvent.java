package com.srliq.app.paper;

/**
 * Publié dans la transaction du rejeu quand un trade simulé devient CLOSED (nouveau trade déjà
 * terminé, ou trade OPEN qui se termine). Écouter après commit.
 */
public record PaperTradeClosedEvent(PaperTrade trade) {
}
