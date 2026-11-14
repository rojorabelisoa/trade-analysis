package com.srliq.app.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Set;

/**
 * Notifications Telegram ({@code srliq.telegram.*}). Sans jeton ou sans identifiant de discussion,
 * les notifications sont désactivées (aucune erreur). Le jeton n'est jamais journalisé ni renvoyé
 * par l'API ({@link #toString()} le masque).
 */
@Validated
@ConfigurationProperties(prefix = "srliq.telegram")
public class TelegramProperties {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    /** Jeton du bot ({@code SRLIQ_TELEGRAM_BOT_TOKEN}). */
    private String botToken = "";

    /** Identifiant de la discussion ({@code SRLIQ_TELEGRAM_CHAT_ID}). */
    private String chatId = "";

    /** Base de l'API Bot (HTTPS ; HTTP accepté uniquement en boucle locale, pour les tests). */
    private String apiBaseUrl = "https://api.telegram.org";

    /** Seuls les événements plus récents que ce délai sont envoyés (pas de rafale sur l'historique). */
    @Min(1)
    @Max(10_080)
    private int maxSignalAgeMinutes = 30;

    @Min(1)
    @Max(60)
    private int connectTimeoutSeconds = 5;

    @Min(1)
    @Max(120)
    private int readTimeoutSeconds = 10;

    public boolean enabled() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    /** Vérifie l'URL de base : HTTPS obligatoire hors boucle locale. */
    public URI validatedBaseUrl() {
        URI u = URI.create(apiBaseUrl.strip().replaceAll("/+$", ""));
        boolean https = "https".equalsIgnoreCase(u.getScheme());
        boolean localHttp = "http".equalsIgnoreCase(u.getScheme()) && u.getHost() != null
                && LOOPBACK.contains(u.getHost().toLowerCase());
        if (!https && !localHttp) {
            throw new IllegalArgumentException("srliq.telegram.api-base-url doit être en HTTPS");
        }
        return u;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken == null ? "" : botToken.strip();
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId == null ? "" : chatId.strip();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getMaxSignalAgeMinutes() {
        return maxSignalAgeMinutes;
    }

    public void setMaxSignalAgeMinutes(int maxSignalAgeMinutes) {
        this.maxSignalAgeMinutes = maxSignalAgeMinutes;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    @Override
    public String toString() {
        return "TelegramProperties{enabled=" + enabled() + ", chatId=" + chatId + ", apiBaseUrl=" + apiBaseUrl
                + ", botToken=" + (botToken.isEmpty() ? "" : "***") + "}";
    }
}
