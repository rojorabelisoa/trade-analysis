package com.srliq.app.trading;

import com.srliq.app.db.Jdbc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class TradingHaltRepository {

    private static final String SELECT = "SELECT id, started_at, ended_at, reason FROM trading_halt";

    private final JdbcClient jdbc;

    public TradingHaltRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Toutes les périodes, de la plus ancienne à la plus récente. */
    public List<TradingHalt> findAll() {
        return jdbc.sql(SELECT + " ORDER BY started_at, id").query(TradingHaltRepository::map).list();
    }

    /** Les {@code limit} périodes les plus récentes. */
    public List<TradingHalt> recent(int limit) {
        return jdbc.sql(SELECT + " ORDER BY started_at DESC, id DESC LIMIT :limit").param("limit", limit)
                .query(TradingHaltRepository::map).list();
    }

    public Optional<TradingHalt> findActive() {
        return jdbc.sql(SELECT + " WHERE ended_at IS NULL").query(TradingHaltRepository::map).optional();
    }

    public Optional<TradingHalt> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(TradingHaltRepository::map).optional();
    }

    public TradingHalt open(Instant start, String reason) {
        long id = jdbc.sql("INSERT INTO trading_halt (started_at, reason) VALUES (:s, :r) RETURNING id")
                .param("s", Jdbc.ts(start)).param("r", reason).query(Long.class).single();
        return findById(id).orElseThrow();
    }

    public Optional<TradingHalt> close(Instant end) {
        return jdbc.sql("UPDATE trading_halt SET ended_at = greatest(:e, started_at) WHERE ended_at IS NULL"
                        + " RETURNING id")
                .param("e", Jdbc.ts(end)).query(Long.class).optional().flatMap(this::findById);
    }

    private static TradingHalt map(ResultSet rs, int row) throws SQLException {
        return new TradingHalt(rs.getLong("id"), Jdbc.instant(rs, "started_at"), Jdbc.instant(rs, "ended_at"),
                rs.getString("reason"));
    }
}
