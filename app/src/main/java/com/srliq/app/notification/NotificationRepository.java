package com.srliq.app.notification;

import com.srliq.app.db.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class NotificationRepository {

    private static final String SELECT = """
            SELECT id, kind, subject_id, channel, status, reason, message, attempts, created_at, updated_at
              FROM notification""";

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Crée la ligne si elle n'existe pas (une par sujet et par canal).
     *
     * @return l'identifiant de la nouvelle ligne, ou vide si elle existait déjà
     */
    public Optional<Long> insertIfAbsent(NotificationKind kind, long subjectId, String channel,
                                         NotificationStatus status, String reason, String message, Instant now) {
        return jdbc.sql("""
                        INSERT INTO notification (kind, subject_id, channel, status, reason, message, attempts,
                                                  created_at, updated_at)
                        VALUES (:kind, :subject, :channel, :status, :reason, :message, 0, :now, :now)
                        ON CONFLICT ON CONSTRAINT notification_unique DO NOTHING
                        RETURNING id""")
                .param("kind", kind.name()).param("subject", subjectId).param("channel", channel)
                .param("status", status.name()).param("reason", truncate(reason)).param("message", message)
                .param("now", Jdbc.ts(now))
                .query(Long.class).optional();
    }

    /** Résultat d'une tentative d'envoi. */
    public void markAttempt(long id, NotificationStatus status, String reason, Instant now) {
        jdbc.sql("""
                        UPDATE notification SET status = :status, reason = :reason, attempts = attempts + 1,
                               updated_at = :now
                         WHERE id = :id""")
                .param("id", id).param("status", status.name()).param("reason", truncate(reason))
                .param("now", Jdbc.ts(now)).update();
    }

    /** Passe de {@code from} à PENDING (réservation pour un nouvel essai) ; faux si l'état a changé. */
    public boolean claim(long id, NotificationStatus from, Instant now) {
        return jdbc.sql("UPDATE notification SET status = 'PENDING', updated_at = :now WHERE id = :id"
                        + " AND status = :from")
                .param("id", id).param("from", from.name()).param("now", Jdbc.ts(now)).update() == 1;
    }

    public Optional<Notification> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(NotificationRepository::map).optional();
    }

    public Optional<Notification> find(NotificationKind kind, long subjectId, String channel) {
        return jdbc.sql(SELECT + " WHERE kind = :kind AND subject_id = :subject AND channel = :channel")
                .param("kind", kind.name()).param("subject", subjectId).param("channel", channel)
                .query(NotificationRepository::map).optional();
    }

    /** Une page de notifications, de la plus récente à la plus ancienne. */
    public record Page(List<Notification> items, int page, int size, long total) {
    }

    public Page search(NotificationKind kind, NotificationStatus status, int page, int size) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (kind != null) {
            where.add("kind = :kind");
            params.put("kind", kind.name());
        }
        if (status != null) {
            where.add("status = :status");
            params.put("status", status.name());
        }
        String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        long total = jdbc.sql("SELECT count(*) FROM notification" + w).params(params).query(Long.class).single();
        List<Notification> items = jdbc.sql(SELECT + w + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query(NotificationRepository::map).list();
        return new Page(items, page, size, total);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 499) + "…";
    }

    private static Notification map(ResultSet rs, int row) throws SQLException {
        return new Notification(rs.getLong("id"), NotificationKind.valueOf(rs.getString("kind")),
                rs.getLong("subject_id"), rs.getString("channel"), NotificationStatus.valueOf(rs.getString("status")),
                rs.getString("reason"), rs.getString("message"), rs.getInt("attempts"),
                Jdbc.instant(rs, "created_at"), Jdbc.instant(rs, "updated_at"));
    }
}
