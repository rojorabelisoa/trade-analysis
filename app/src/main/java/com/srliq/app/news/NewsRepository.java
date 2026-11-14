package com.srliq.app.news;

import com.srliq.app.db.Jdbc;
import com.srliq.engine.filter.NewsCalendar;
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
public class NewsRepository {

    private static final String SELECT = "SELECT id, event_time, currency, impact, event FROM news_event";

    private final JdbcClient jdbc;

    public NewsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Annonces dans {@code [from, to)} (bornes facultatives), par heure. */
    public List<NewsEvent> find(Instant from, Instant to) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (from != null) {
            where.add("event_time >= :from");
            params.put("from", Jdbc.ts(from));
        }
        if (to != null) {
            where.add("event_time < :to");
            params.put("to", Jdbc.ts(to));
        }
        String sql = SELECT + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY event_time, id";
        return jdbc.sql(sql).params(params).query(NewsRepository::map).list();
    }

    public NewsEvent insert(Instant time, String currency, String impact, String event) {
        long id = jdbc.sql("""
                        INSERT INTO news_event (event_time, currency, impact, event)
                        VALUES (:t, :c, :i, :e) RETURNING id""")
                .param("t", Jdbc.ts(time)).param("c", currency).param("i", impact).param("e", event)
                .query(Long.class).single();
        return findById(id).orElseThrow();
    }

    public Optional<NewsEvent> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(NewsRepository::map).optional();
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM news_event WHERE id = :id").param("id", id).update() > 0;
    }

    /** Toutes les annonces au format du moteur (le filtre d'impact est appliqué par {@link NewsCalendar}). */
    public List<NewsCalendar.Event> engineEvents() {
        return find(null, null).stream()
                .map(n -> new NewsCalendar.Event(n.time(), n.currency(), n.impact(), n.event()))
                .toList();
    }

    private static NewsEvent map(ResultSet rs, int row) throws SQLException {
        return new NewsEvent(rs.getLong("id"), Jdbc.instant(rs, "event_time"), rs.getString("currency"),
                rs.getString("impact"), rs.getString("event"));
    }
}
