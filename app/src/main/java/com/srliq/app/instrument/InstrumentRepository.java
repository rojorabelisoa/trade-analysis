package com.srliq.app.instrument;

import com.srliq.app.web.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class InstrumentRepository {

    private final JdbcClient jdbc;

    public InstrumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Instrument> findAll() {
        return jdbc.sql("SELECT id, symbol, name, point_value FROM instrument ORDER BY symbol")
                .query(InstrumentRepository::map).list();
    }

    /** Recherche après normalisation ("GC1!" trouve GC). */
    public Optional<Instrument> findBySymbol(String symbol) {
        return jdbc.sql("SELECT id, symbol, name, point_value FROM instrument WHERE symbol = :s")
                .param("s", Symbols.normalize(symbol))
                .query(InstrumentRepository::map).optional();
    }

    /** Instrument connu, sinon 400. */
    public Instrument require(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw ApiException.badRequest("Symbole manquant");
        }
        return findBySymbol(symbol).orElseThrow(() -> ApiException.badRequest(
                "Instrument inconnu : '" + symbol.strip() + "'"));
    }

    private static Instrument map(ResultSet rs, int row) throws SQLException {
        return new Instrument(rs.getLong("id"), rs.getString("symbol"), rs.getString("name"),
                rs.getDouble("point_value"));
    }
}
