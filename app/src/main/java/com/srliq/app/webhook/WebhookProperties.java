package com.srliq.app.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code srliq.webhook.token} (variable d'environnement {@code SRLIQ_WEBHOOK_TOKEN}) : secret placé
 * dans l'URL de l'alerte TradingView. Absent ou trop court : l'endpoint répond 503.
 * Jamais journalisé ({@link #toString()} le masque).
 */
@ConfigurationProperties(prefix = "srliq.webhook")
public class WebhookProperties {

    /** Longueur minimale acceptée pour le secret. */
    public static final int MIN_TOKEN_LENGTH = 16;

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "WebhookProperties[token=" + (token == null || token.isBlank() ? "<absent>" : "<masqué>") + "]";
    }
}
