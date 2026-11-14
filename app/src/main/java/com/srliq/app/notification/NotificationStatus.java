package com.srliq.app.notification;

/** PENDING : envoi en cours (transitoire). SKIPPED : volontairement non envoyée (ancienne, désactivée). */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
