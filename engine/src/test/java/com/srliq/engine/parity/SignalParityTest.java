package com.srliq.engine.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.srliq.engine.backtest.BacktestSummary;
import com.srliq.engine.backtest.Backtester;
import com.srliq.engine.backtest.IntradayRules;
import com.srliq.engine.model.Signal;
import com.srliq.engine.model.Trade;
import com.srliq.engine.strategy.SignalEngine;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parité signaux + backtest avec la référence Python, pour chaque scénario de {@code index.json}.
 */
class SignalParityTest {

    private static final double PRICE_TOL = 1e-6;
    private static final double RR_TOL = 1e-9;
    private static final double SUMMARY_TOL = 1e-9;

    static List<String> scenarios() {
        return ParityFixtures.scenarioNames();
    }

    private static List<Signal> javaSignals(ParityFixtures.ScenarioInputs in) {
        return SignalEngine.generate(in.zones(), in.ltf(), in.symbol(), in.config(), in.bias(), in.news(), in.trend());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void signalsMatchReference(String name) {
        ParityFixtures.ScenarioInputs in = ParityFixtures.scenario(name);
        List<Signal> actual = javaSignals(in);
        JsonNode expected = in.doc().get("signals");

        List<String> errors = new ArrayList<>();
        int n = Math.min(actual.size(), expected.size());
        for (int k = 0; k < n; k++) {
            compareSignal(k, expected.get(k), actual.get(k), errors);
            if (!errors.isEmpty()) {
                break; // la première divergence suffit, les suivantes en découlent souvent
            }
        }
        if (errors.isEmpty() && actual.size() != expected.size()) {
            String extra = actual.size() > expected.size()
                    ? "signal Java en trop : " + actual.get(n)
                    : "signal Python manquant : " + expected.get(n);
            errors.add("nombre de signaux : attendu " + expected.size() + ", obtenu " + actual.size()
                    + " (premier écart à l'index " + n + ", " + extra + ")");
        }
        if (!errors.isEmpty()) {
            fail("Scénario " + name + " : " + String.join("; ", errors));
        }
    }

    private static void compareSignal(int k, JsonNode e, Signal a, List<String> errors) {
        String where = "signal #" + k + " (attendu " + e.get("time").asText() + " " + e.get("direction").asText()
                + ", obtenu " + a.time() + " " + a.direction() + ")";
        checkEq(errors, where, "time", e.get("time").asText(), a.time().toString());
        checkEq(errors, where, "direction", e.get("direction").asText(), a.direction().pyName());
        checkEq(errors, where, "sweep_time", e.get("sweep_time").asText(), a.sweepTime().toString());
        checkEq(errors, where, "bias", e.get("bias").asText(), a.bias());
        checkEq(errors, where, "session", e.get("session").asText(), a.session());
        checkEq(errors, where, "zone", e.get("zone").asText(), a.zone());
        checkEq(errors, where, "symbol", e.get("symbol").asText(), a.symbol());
        checkNum(errors, where, "entry", e.get("entry").asDouble(), a.entry(), PRICE_TOL);
        checkNum(errors, where, "stop", e.get("stop").asDouble(), a.stop(), PRICE_TOL);
        checkNum(errors, where, "target", e.get("target").asDouble(), a.target(), PRICE_TOL);
        checkNum(errors, where, "sweep_extreme", e.get("sweep_extreme").asDouble(), a.sweepExtreme(), PRICE_TOL);
        checkNum(errors, where, "rr", e.get("rr").asDouble(), a.rr(), RR_TOL);
        JsonNode nz = e.get("next_zone");
        if (nz == null || nz.isNull()) {
            if (a.nextZone() != null) {
                errors.add(where + " next_zone : attendu null, obtenu " + a.nextZone());
            }
        } else if (a.nextZone() == null) {
            errors.add(where + " next_zone : attendu " + nz.asDouble() + ", obtenu null");
        } else {
            checkNum(errors, where, "next_zone", nz.asDouble(), a.nextZone(), PRICE_TOL);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void backtestMatchesReference(String name) {
        ParityFixtures.ScenarioInputs in = ParityFixtures.scenario(name);
        List<Signal> signals = javaSignals(in);
        for (JsonNode bt : in.doc().get("backtests")) {
            boolean intraday = bt.get("intraday").asBoolean();
            String variant = "scénario " + name + (intraday ? " [intraday]" : " [sans intraday]");
            List<Trade> trades = Backtester.run(in.ltf(), signals, bt.get("cost").asDouble(),
                    bt.get("max_hold_bars").asInt(), intraday ? IntradayRules.defaults() : null);
            JsonNode exp = bt.get("trades");
            List<String> errors = new ArrayList<>();
            int n = Math.min(exp.size(), trades.size());
            for (int k = 0; k < n && errors.isEmpty(); k++) {
                JsonNode e = exp.get(k);
                Trade t = trades.get(k);
                String where = "trade #" + k + " (signal " + e.get("signal_time").asText() + ")";
                checkEq(errors, where, "signal_time", e.get("signal_time").asText(), t.signal().time().toString());
                checkEq(errors, where, "entry_time", e.get("entry_time").asText(), t.entryTime().toString());
                checkEq(errors, where, "exit_time", e.get("exit_time").asText(), t.exitTime().toString());
                checkEq(errors, where, "outcome", e.get("outcome").asText(), t.outcome().pyName());
                checkNum(errors, where, "entry", e.get("entry").asDouble(), t.entry(), PRICE_TOL);
                checkNum(errors, where, "exit", e.get("exit").asDouble(), t.exit(), PRICE_TOL);
                checkNum(errors, where, "r", e.get("r").asDouble(), t.r(), PRICE_TOL);
            }
            if (errors.isEmpty() && exp.size() != trades.size()) {
                errors.add("nombre de trades : attendu " + exp.size() + ", obtenu " + trades.size()
                        + " (premier écart à l'index " + n + ")");
            }
            if (errors.isEmpty()) {
                compareSummary(bt.get("summary"), BacktestSummary.of(trades).toMap(), errors);
            }
            if (!errors.isEmpty()) {
                fail(variant + " : " + String.join("; ", errors));
            }
        }
    }

    private static void compareSummary(JsonNode exp, Map<String, Object> act, List<String> errors) {
        List<String> expKeys = new ArrayList<>();
        exp.properties().forEach(f -> expKeys.add(f.getKey()));
        assertEquals(expKeys, new ArrayList<>(act.keySet()), "clés du summary");
        for (Map.Entry<String, JsonNode> f : exp.properties()) {
            Object a = act.get(f.getKey());
            JsonNode e = f.getValue();
            if (e.isTextual()) {
                checkEq(errors, "summary", f.getKey(), e.asText(), String.valueOf(a));
            } else if (e.isIntegralNumber()) {
                if (!(a instanceof Integer) || (Integer) a != e.asInt()) {
                    errors.add("summary " + f.getKey() + " : attendu " + e.asInt() + ", obtenu " + a);
                }
            } else {
                double ev = e.asDouble();
                double av = ((Number) a).doubleValue();
                if (Double.isInfinite(ev) || Double.isInfinite(av)) {
                    if (ev != av) {
                        errors.add("summary " + f.getKey() + " : attendu " + ev + ", obtenu " + av);
                    }
                } else {
                    checkNum(errors, "summary", f.getKey(), ev, av, SUMMARY_TOL);
                }
            }
        }
    }

    private static void checkEq(List<String> errors, String where, String field, String exp, String act) {
        if (!exp.equals(act)) {
            errors.add(where + " " + field + " : attendu '" + exp + "', obtenu '" + act + "'");
        }
    }

    private static void checkNum(List<String> errors, String where, String field, double exp, double act,
                                 double tol) {
        if (!(Math.abs(exp - act) <= tol)) {
            errors.add(where + " " + field + " : attendu " + exp + ", obtenu " + act + " (écart "
                    + Math.abs(exp - act) + ")");
        }
    }
}
