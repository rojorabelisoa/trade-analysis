package com.srliq.app.bias;

import com.srliq.engine.filter.BiasBook;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class BiasRepository {

    private static final String SELECT = """
            SELECT b.id, b.effective_date, i.symbol, b.bias, b.note
              FROM fundamental_bias b JOIN instrument i ON i.id = b.instrument_id""";

    private final JdbcClient jdbc;

    public BiasRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Toutes les lignes, éventuellement pour un seul instrument, par date puis symbole. */
    public List<BiasEntry> findAll(String symbol) {
        if (symbol == null) {
            return jdbc.sql(SELECT + " ORDER BY b.effective_date, i.symbol").query(BiasRepository::map).list();
        }
        return jdbc.sql(SELECT + " WHERE i.symbol = :s ORDER BY b.effective_date")
                .param("s", symbol).query(BiasRepository::map).list();
    }

    public BiasEntry insert(long instrumentId, LocalDate date, String bias, String note) {
        long id = jdbc.sql("""
                        INSERT INTO fundamental_bias (effective_date, instrument_id, bias, note)
                        VALUES (:d, :i, :b, :n) RETURNING id""")
                .param("d", date).param("i", instrumentId).param("b", bias).param("n", note)
                .query(Long.class).single();
        return findById(id).orElseThrow();
    }

    public Optional<BiasEntry> findById(long id) {
        return jdbc.sql(SELECT + " WHERE b.id = :id").param("id", id).query(BiasRepository::map).optional();
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM fundamental_bias WHERE id = :id").param("id", id).update() > 0;
    }

    /**
     * Lignes au format du moteur, tous instruments confondus. L'appelant garde celles de son
     * instrument : un instrument sans aucune ligne est "neutral".
     */
    public List<BiasBook.Entry> engineEntries() {
        return findAll(null).stream()
                .map(b -> new BiasBook.Entry(b.effectiveDate().atStartOfDay(ZoneOffset.UTC).toInstant(), b.symbol(),
                        b.bias()))
                .toList();
    }

    private static BiasEntry map(ResultSet rs, int row) throws SQLException {
        return new BiasEntry(rs.getLong("id"), rs.getObject("effective_date", LocalDate.class),
                rs.getString("symbol"), rs.getString("bias"), rs.getString("note"));
    }
}
