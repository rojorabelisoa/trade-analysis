package com.srliq.app.notification;

import java.time.Instant;

/**
 * Notification (table {@code notification}) : une ligne par sujet et par canal.
 *
 * @param subjectId identifiant du signal (SIGNAL), du trade simulé (TRADE_CLOSED) ou de la période
 *                  d'arrêt (HALT, RESUME)
 * @param reason    motif d'un SKIPPED ou d'un FAILED
 * @param message   texte envoyé (ou qui l'aurait été)
 */
public record Notification(long id, NotificationKind kind, long subjectId, String channel,
                           NotificationStatus status, String reason, String message, int attempts,
                           Instant createdAt, Instant updatedAt) {

    public static final String TELEGRAM = "TELEGRAM";
}
