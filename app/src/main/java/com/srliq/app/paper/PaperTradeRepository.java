package com.srliq.app.paper;

import com.srliq.app.db.Jdbc;
import com.srliq.engine.model.Direction;
import com.srliq.engine.model.Outcome;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PaperTradeRepository {

    private static final String COLUMNS = """
            t.id, i.symbol, t.timeframe, t.signal_id, t.signal_time, t.direction, t.status, t.entry_time, t.entry,
            t.stop, t.target, t.risk_points, t.exit_time, t.exit_price, t.outcome, t.last_time, t.last_price, t.r,
            t.cost, t.quantity, t.point_value, t.pnl_currency, t.risk_too_large, s.session, s.bias, t.created_at,
            t.updated_at""";

    private static final String FROM = " FROM paper_trade t"
            + " JOIN instrument i ON i.id = t.instrument_id"
            + " JOIN signal s ON s.id = t.signal_id";

    /** Valeurs calculées par le rejeu pour un trade (tout sauf l'identifiant et les dates d'écriture). */
    public record Values(long signalId, Instant signalTime, Direction direction, PaperTradeStatus status,
                         Instant entryTime, double entry, double stop, double target, double riskPoints,
                         Instant exitTime, Double exitPrice, Outcome outcome, Instant lastTime, double lastPrice,
                         double r, double cost, int quantity, double pointValue, double pnlCurrency,
                         boolean riskTooLarge) {

        /** Mêmes valeurs que le trade stocké (comparaison exacte : le rejeu est déterministe). */
        public boolean sameAs(PaperTrade t) {
            return signalId == t.signalId() && signalTime.equals(t.signalTime()) && direction == t.direction()
                    && status == t.status() && entryTime.equals(t.entryTime()) && entry == t.entry()
                    && stop == t.stop() && target == t.target() && riskPoints == t.riskPoints()
                    && Objects.equals(exitTime, t.exitTime()) && Objects.equals(exitPrice, t.exitPrice())
                    && outcome == t.outcome() && lastTime.equals(t.lastTime()) && lastPrice == t.lastPrice()
                    && r == t.r() && cost == t.cost() && quantity == t.quantity() && pointValue == t.pointValue()
                    && pnlCurrency == t.pnlCurrency() && riskTooLarge == t.riskTooLarge();
        }
    }

    private final JdbcClient jdbc;

    public PaperTradeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Verrou transactionnel par instrument : deux rejeux du même instrument ne se chevauchent pas. */
    public void lockInstrument(long instrumentId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:k1, :k2)").param("k1", 0x5052_4C51).param("k2", (int) instrumentId)
                .query((rs, row) -> 1).list();
    }

    public List<PaperTrade> findFor(long instrumentId, String timeframe) {
        return jdbc.sql("SELECT " + COLUMNS + FROM + " WHERE t.instrument_id = :i AND t.timeframe = :tf"
                        + " ORDER BY t.entry_time, t.id")
                .param("i", instrumentId).param("tf", timeframe).query(PaperTradeRepository::map).list();
    }

    public Optional<PaperTrade> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + FROM + " WHERE t.id = :id").param("id", id)
                .query(PaperTradeRepository::map).optional();
    }

    public long insert(long instrumentId, String timeframe, Values v, Instant now) {
        return bind(jdbc.sql("""
                        INSERT INTO paper_trade (instrument_id, timeframe, signal_id, signal_time, direction, status,
                                                 entry_time, entry, stop, target, risk_points, exit_time, exit_price,
                                                 outcome, last_time, last_price, r, cost, quantity, point_value,
                                                 pnl_currency, risk_too_large, created_at, updated_at)
                        VALUES (:instrumentId, :timeframe, :signalId, :signalTime, :direction, :status, :entryTime,
                                :entry, :stop, :target, :riskPoints, :exitTime, :exitPrice, :outcome, :lastTime,
                                :lastPrice, :r, :cost, :quantity, :pointValue, :pnl, :tooLarge, :now, :now)
                        RETURNING id"""), v)
                .param("instrumentId", instrumentId).param("timeframe", timeframe).param("now", Jdbc.ts(now))
                .query(Long.class).single();
    }

    public void update(long id, Values v, Instant now) {
        bind(jdbc.sql("""
                        UPDATE paper_trade
                           SET signal_id = :signalId, signal_time = :signalTime, direction = :direction,
                               status = :status, entry_time = :entryTime, entry = :entry, stop = :stop,
                               target = :target, risk_points = :riskPoints, exit_time = :exitTime,
                               exit_price = :exitPrice, outcome = :outcome, last_time = :lastTime,
                               last_price = :lastPrice, r = :r, cost = :cost, quantity = :quantity,
                               point_value = :pointValue, pnl_currency = :pnl, risk_too_large = :tooLarge,
                               updated_at = :now
                         WHERE id = :id"""), v)
                .param("id", id).param("now", Jdbc.ts(now)).update();
    }

    public int delete(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.sql("DELETE FROM paper_trade WHERE id IN (:ids)").param("ids", ids).update();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Values v) {
        return spec.param("signalId", v.signalId())
                .param("signalTime", Jdbc.ts(v.signalTime()))
                .param("direction", v.direction().pyName())
                .param("status", v.status().name())
                .param("entryTime", Jdbc.ts(v.entryTime()))
                .param("entry", v.entry())
                .param("stop", v.stop())
                .param("target", v.target())
                .param("riskPoints", v.riskPoints())
                .param("exitTime", Jdbc.ts(v.exitTime()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("exitPrice", v.exitPrice(), Types.DOUBLE)
                .param("outcome", v.outcome() == null ? null : v.outcome().pyName(), Types.VARCHAR)
                .param("lastTime", Jdbc.ts(v.lastTime()))
                .param("lastPrice", v.lastPrice())
                .param("r", v.r())
                .param("cost", v.cost())
                .param("quantity", v.quantity())
                .param("pointValue", v.pointValue())
                .param("pnl", v.pnlCurrency())
                .param("tooLarge", v.riskTooLarge());
    }

    /** Critères ; un champ {@code null} ne filtre pas. Intervalle {@code [from, to)} sur l'heure du signal. */
    public record Filter(String symbol, String timeframe, PaperTradeStatus status, Instant from, Instant to) {
    }

    /** Une page de trades, du plus récent au plus ancien. */
    public record Page(List<PaperTrade> items, int page, int size, long total) {
    }

    public Page search(Filter f, int page, int size) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        where(f, where, params);
        String from = FROM + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where));
        long total = jdbc.sql("SELECT count(*)" + from).params(params).query(Long.class).single();
        List<PaperTrade> items = jdbc.sql("SELECT " + COLUMNS + from
                        + " ORDER BY t.signal_time DESC, t.id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query(PaperTradeRepository::map).list();
        return new Page(items, page, size, total);
    }

    /** Tous les trades correspondant au filtre, par heure d'entrée (ordre du backtest). */
    public List<PaperTrade> findAll(Filter f) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        where(f, where, params);
        return jdbc.sql("SELECT " + COLUMNS + FROM + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                        + " ORDER BY t.entry_time, t.signal_time, t.id")
                .params(params).query(PaperTradeRepository::map).list();
    }

    private static void where(Filter f, List<String> where, Map<String, Object> params) {
        if (f.symbol() != null) {
            where.add("i.symbol = :symbol");
            params.put("symbol", f.symbol());
        }
        if (f.timeframe() != null) {
            where.add("t.timeframe = :timeframe");
            params.put("timeframe", f.timeframe());
        }
        if (f.status() != null) {
            where.add("t.status = :status");
            params.put("status", f.status().name());
        }
        if (f.from() != null) {
            where.add("t.signal_time >= :from");
            params.put("from", Jdbc.ts(f.from()));
        }
        if (f.to() != null) {
            where.add("t.signal_time < :to");
            params.put("to", Jdbc.ts(f.to()));
        }
    }

    private static PaperTrade map(ResultSet rs, int row) throws SQLException {
        String outcome = rs.getString("outcome");
        return new PaperTrade(
                rs.getLong("id"),
                rs.getString("symbol"),
                rs.getString("timeframe"),
                rs.getLong("signal_id"),
                Jdbc.instant(rs, "signal_time"),
                Direction.fromPython(rs.getString("direction")),
                PaperTradeStatus.valueOf(rs.getString("status")),
                Jdbc.instant(rs, "entry_time"),
                rs.getDouble("entry"),
                rs.getDouble("stop"),
                rs.getDouble("target"),
                rs.getDouble("risk_points"),
                Jdbc.instant(rs, "exit_time"),
                Jdbc.nullableDouble(rs, "exit_price"),
                outcome == null ? null : Outcome.fromPython(outcome),
                Jdbc.instant(rs, "last_time"),
                rs.getDouble("last_price"),
                rs.getDouble("r"),
                rs.getDouble("cost"),
                rs.getInt("quantity"),
                rs.getDouble("point_value"),
                rs.getDouble("pnl_currency"),
                rs.getBoolean("risk_too_large"),
                rs.getString("session"),
                rs.getString("bias"),
                Jdbc.instant(rs, "created_at"),
                Jdbc.instant(rs, "updated_at"));
    }
}
