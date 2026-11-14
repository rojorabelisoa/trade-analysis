package com.srliq.app.marketdata;

import com.srliq.app.db.Jdbc;
import com.srliq.engine.model.Candle;
import com.srliq.engine.model.CandleSeries;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CandleRepository {

    private static final int CHUNK = 5_000;

    /**
     * Upsert ensembliste : une requête par paquet, avec des tableaux. Une ligne existante n'est
     * réécrite que si ses valeurs changent ; RETURNING ne renvoie que les lignes insérées ou
     * modifiées ({@code xmax = 0} distingue une insertion d'une mise à jour).
     */
    private static final String UPSERT = """
            INSERT INTO candle AS c (instrument_id, timeframe_seconds, open_time, open, high, low, close, volume)
            SELECT ?, ?, timestamptz 'epoch' + t.ms * interval '1 millisecond', t.o, t.h, t.l, t.c, t.v
              FROM unnest(?::bigint[], ?::float8[], ?::float8[], ?::float8[], ?::float8[], ?::float8[])
                   AS t(ms, o, h, l, c, v)
            ON CONFLICT (instrument_id, timeframe_seconds, open_time) DO UPDATE
               SET open = excluded.open, high = excluded.high, low = excluded.low,
                   close = excluded.close, volume = excluded.volume
             WHERE (c.open, c.high, c.low, c.close, c.volume)
                   IS DISTINCT FROM (excluded.open, excluded.high, excluded.low, excluded.close, excluded.volume)
            RETURNING (xmax = 0) AS inserted
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public CandleRepository(JdbcTemplate jdbcTemplate, JdbcClient jdbc) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
    }

    /** Compteurs d'un upsert. */
    public record UpsertCount(int inserted, int updated, int unchanged) {
    }

    public UpsertCount upsert(long instrumentId, Duration timeframe, CandleSeries series) {
        int inserted = 0;
        int updated = 0;
        int n = series.size();
        for (int from = 0; from < n; from += CHUNK) {
            CandleSeries part = series.slice(from, Math.min(n, from + CHUNK));
            List<Boolean> flags = jdbcTemplate.execute((Connection con) -> {
                try (PreparedStatement ps = con.prepareStatement(UPSERT)) {
                    ps.setLong(1, instrumentId);
                    ps.setInt(2, Math.toIntExact(timeframe.toSeconds()));
                    ps.setArray(3, longs(con, part.timesMillis()));
                    ps.setArray(4, doubles(con, part.opens()));
                    ps.setArray(5, doubles(con, part.highs()));
                    ps.setArray(6, doubles(con, part.lows()));
                    ps.setArray(7, doubles(con, part.closes()));
                    ps.setArray(8, doubles(con, part.volumes()));
                    List<Boolean> out = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(rs.getBoolean(1));
                        }
                    }
                    return out;
                }
            });
            for (Boolean f : flags) {
                if (f) {
                    inserted++;
                } else {
                    updated++;
                }
            }
        }
        return new UpsertCount(inserted, updated, n - inserted - updated);
    }

    /** Timeframes (en secondes) présents pour un instrument. */
    public List<Integer> timeframes(long instrumentId) {
        return jdbc.sql("SELECT DISTINCT timeframe_seconds FROM candle WHERE instrument_id = :id ORDER BY 1")
                .param("id", instrumentId).query(Integer.class).list();
    }

    public int count(long instrumentId, Duration timeframe) {
        return jdbc.sql("SELECT count(*) FROM candle WHERE instrument_id = :id AND timeframe_seconds = :tf")
                .param("id", instrumentId).param("tf", Math.toIntExact(timeframe.toSeconds()))
                .query(Integer.class).single();
    }

    /** Toutes les bougies d'un instrument et d'un timeframe, triées par heure d'ouverture. */
    public CandleSeries load(long instrumentId, Duration timeframe) {
        List<Candle> candles = jdbc.sql("""
                        SELECT open_time, open, high, low, close, volume
                          FROM candle
                         WHERE instrument_id = :id AND timeframe_seconds = :tf
                         ORDER BY open_time""")
                .param("id", instrumentId).param("tf", Math.toIntExact(timeframe.toSeconds()))
                .query((rs, row) -> new Candle(Jdbc.instant(rs, "open_time"), rs.getDouble("open"),
                        rs.getDouble("high"), rs.getDouble("low"), rs.getDouble("close"), rs.getDouble("volume")))
                .list();
        return CandleSeries.of(candles);
    }

    private static Array longs(Connection con, long[] values) throws java.sql.SQLException {
        Long[] boxed = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return con.createArrayOf("int8", boxed);
    }

    private static Array doubles(Connection con, double[] values) throws java.sql.SQLException {
        Double[] boxed = new Double[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return con.createArrayOf("float8", boxed);
    }
}
