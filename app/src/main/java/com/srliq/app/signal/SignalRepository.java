package com.srliq.app.signal;

import com.srliq.app.db.Jdbc;
import com.srliq.engine.model.Direction;
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
public class SignalRepository {

    private static final String COLUMNS = """
            s.id, i.symbol, s.source, s.timeframe, s.signal_time, s.direction, s.entry, s.stop, s.target, s.rr,
            s.zone, s.contacts, s.sweep_time, s.sweep_extreme, s.bias, s.session, s.next_zone, s.received_at""";

    private final JdbcClient jdbc;

    public SignalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insère le signal s'il n'existe pas déjà (même instrument, source, heure et sens).
     *
     * @return l'identifiant du nouveau signal, ou vide si c'était un doublon
     */
    public Optional<Long> insertIfAbsent(SignalRecord s) {
        return jdbc.sql("""
                        INSERT INTO signal (instrument_id, source, timeframe, signal_time, direction, entry, stop,
                                            target, rr, zone, contacts, sweep_time, sweep_extreme, bias, session,
                                            next_zone, received_at)
                        SELECT i.id, :source, :timeframe, :time, :direction, :entry, :stop, :target, :rr, :zone,
                               :contacts, :sweepTime, :sweepExtreme, :bias, :session, :nextZone, :receivedAt
                          FROM instrument i
                         WHERE i.symbol = :symbol
                        ON CONFLICT ON CONSTRAINT signal_unique DO NOTHING
                        RETURNING id""")
                .param("symbol", s.symbol())
                .param("source", s.source().name())
                .param("timeframe", s.timeframe())
                .param("time", Jdbc.ts(s.time()))
                .param("direction", s.direction().pyName())
                .param("entry", s.entry())
                .param("stop", s.stop())
                .param("target", s.target())
                .param("rr", s.rr())
                .param("zone", s.zone())
                .param("contacts", s.contacts(), java.sql.Types.INTEGER)
                .param("sweepTime", Jdbc.ts(s.sweepTime()), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("sweepExtreme", s.sweepExtreme(), java.sql.Types.DOUBLE)
                .param("bias", s.bias())
                .param("session", s.session())
                .param("nextZone", s.nextZone(), java.sql.Types.DOUBLE)
                .param("receivedAt", Jdbc.ts(s.receivedAt()))
                .query(Long.class).optional();
    }

    public Optional<SignalRecord> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM signal s JOIN instrument i ON i.id = s.instrument_id"
                        + " WHERE s.id = :id")
                .param("id", id).query(SignalRepository::map).optional();
    }

    /** Signal existant ayant la même clé d'unicité. */
    public Optional<SignalRecord> findByKey(String symbol, SignalSource source, Instant time, Direction direction) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM signal s JOIN instrument i ON i.id = s.instrument_id"
                        + " WHERE i.symbol = :symbol AND s.source = :source AND s.signal_time = :time"
                        + " AND s.direction = :direction")
                .param("symbol", symbol).param("source", source.name()).param("time", Jdbc.ts(time))
                .param("direction", direction.pyName())
                .query(SignalRepository::map).optional();
    }

    /** Critères de recherche ; un champ {@code null} ne filtre pas. Intervalle {@code [from, to)}. */
    public record Filter(String symbol, SignalSource source, Instant from, Instant to) {
    }

    /** Une page de signaux, du plus récent au plus ancien. */
    public record Page(List<SignalRecord> items, int page, int size, long total) {
    }

    public Page search(Filter f, int page, int size) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.symbol() != null) {
            where.add("i.symbol = :symbol");
            params.put("symbol", f.symbol());
        }
        if (f.source() != null) {
            where.add("s.source = :source");
            params.put("source", f.source().name());
        }
        if (f.from() != null) {
            where.add("s.signal_time >= :from");
            params.put("from", Jdbc.ts(f.from()));
        }
        if (f.to() != null) {
            where.add("s.signal_time < :to");
            params.put("to", Jdbc.ts(f.to()));
        }
        String from = " FROM signal s JOIN instrument i ON i.id = s.instrument_id"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where));
        long total = jdbc.sql("SELECT count(*)" + from).params(params).query(Long.class).single();
        List<SignalRecord> items = jdbc.sql("SELECT " + COLUMNS + from
                        + " ORDER BY s.signal_time DESC, s.id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query(SignalRepository::map).list();
        return new Page(items, page, size, total);
    }

    private static SignalRecord map(ResultSet rs, int row) throws SQLException {
        return new SignalRecord(
                rs.getLong("id"),
                rs.getString("symbol"),
                SignalSource.valueOf(rs.getString("source")),
                rs.getString("timeframe"),
                Jdbc.instant(rs, "signal_time"),
                Direction.fromPython(rs.getString("direction")),
                rs.getDouble("entry"),
                rs.getDouble("stop"),
                rs.getDouble("target"),
                rs.getDouble("rr"),
                rs.getString("zone"),
                Jdbc.nullableInt(rs, "contacts"),
                Jdbc.instant(rs, "sweep_time"),
                Jdbc.nullableDouble(rs, "sweep_extreme"),
                rs.getString("bias"),
                rs.getString("session"),
                Jdbc.nullableDouble(rs, "next_zone"),
                Jdbc.instant(rs, "received_at"));
    }
}
