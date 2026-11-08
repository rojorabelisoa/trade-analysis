import numpy as np
import pandas as pd
import pytest

from trade_analysis.backtest import run_backtest, summarize
from trade_analysis.data import infer_bar_duration, load_tradingview_csv, resample
from trade_analysis.filters import BiasBook, NewsCalendar
from trade_analysis.message import format_signal
from trade_analysis.strategy import Config, Signal, generate_signals
from trade_analysis.structure import find_pivots


def synthetic_5m(n=6000, seed=1, start="2026-01-05"):
    """Marché en range (retour à la moyenne) : beaucoup de sweeps de niveaux."""
    rng = np.random.default_rng(seed)
    px = np.empty(n)
    px[0] = 2000.0
    for i in range(1, n):
        px[i] = px[i - 1] + 0.02 * (2000 - px[i - 1]) + rng.normal(0, 1.2)
    idx = pd.date_range(start, periods=n, freq="5min", tz="UTC")
    o = np.concatenate([[px[0]], px[:-1]])
    wick = np.abs(rng.normal(0, 0.8, (2, n)))
    return pd.DataFrame({
        "open": o, "close": px,
        "high": np.maximum(o, px) + wick[0], "low": np.minimum(o, px) - wick[1],
        "volume": 1.0,
    }, index=pd.DatetimeIndex(idx, name="time"))


def test_load_tradingview_unix_and_iso(tmp_path):
    p1 = tmp_path / "a.csv"
    p1.write_text("time,open,high,low,close,Volume\n1767600000,1,2,0.5,1.5,10\n1767600300,1.5,2,1,1.8,5\n")
    df = load_tradingview_csv(str(p1))
    assert df.index.tz is not None and len(df) == 2
    assert infer_bar_duration(df) == pd.Timedelta(minutes=5)
    p2 = tmp_path / "b.csv"
    p2.write_text("time,open,high,low,close\n2026-01-05T00:00:00Z,1,2,0.5,1.5\n")
    assert load_tradingview_csv(str(p2))["volume"].iloc[0] == 0.0


def test_pivot_confirmation_delay():
    df = synthetic_5m(300)
    for p in find_pivots(df, 3, 2, pd.Timedelta(minutes=5)):
        assert p.confirmed_at == p.time + pd.Timedelta(minutes=5) * 3


def test_signals_generated_and_consistent():
    ltf = synthetic_5m()
    htf = resample(ltf, "1h")
    # Objectif R:R fixe, stop sur la mèche
    sigs = generate_signals(htf, ltf, "GC", Config(target_mode="rr", stop_mode="sweep"))
    assert len(sigs) > 0
    for s in sigs:
        if s.direction == "long":
            assert s.stop < s.sweep_extreme < s.entry < s.target
        else:
            assert s.target < s.entry < s.sweep_extreme < s.stop
        assert s.target - s.entry == pytest.approx(s.rr * (s.entry - s.stop))
    # Stop et objectif structurels (H4) : stop au moins aussi loin que la mèche, R:R >= minimum
    cfg = Config(trend_mode="off")
    for s in generate_signals(htf, ltf, "GC", cfg, trend_htf=resample(ltf, "4h")):
        sign = 1 if s.direction == "long" else -1
        assert sign * (s.sweep_extreme - s.stop) > 0
        assert sign * (s.target - s.entry) / (sign * (s.entry - s.stop)) >= cfg.min_rr - 1e-9


def test_no_lookahead():
    """Couper les données ne doit jamais changer un signal déjà émis."""
    ltf = synthetic_5m()
    full = generate_signals(resample(ltf, "1h"), ltf, "GC")
    cut = ltf.index[len(ltf) * 2 // 3]
    ltf_cut = ltf[ltf.index < cut]
    # HTF tronqué aux bougies complètes avant la coupure
    htf_cut = resample(ltf_cut, "1h")
    htf_cut = htf_cut[htf_cut.index + pd.Timedelta(hours=1) <= cut]
    part = generate_signals(htf_cut, ltf_cut, "GC")
    expected = [s for s in full if s.time <= ltf_cut.index[-1] + pd.Timedelta(minutes=5)]
    assert [(s.time, s.direction, s.entry, s.stop) for s in part] == \
           [(s.time, s.direction, s.entry, s.stop) for s in expected]


def test_bias_filter_blocks_direction():
    ltf = synthetic_5m()
    htf = resample(ltf, "1h")
    bias = BiasBook(pd.DataFrame({"date": ["2026-01-01"], "symbol": ["GC"], "bias": ["long"]}), "GC")
    cfg = Config(trend_mode="off", target_mode="rr", stop_mode="sweep")
    sigs = generate_signals(htf, ltf, "GC", cfg, bias=bias)
    assert sigs and all(s.direction == "long" for s in sigs)
    off = BiasBook(pd.DataFrame({"date": ["2026-01-01"], "symbol": ["GC"], "bias": ["off"]}), "GC")
    assert generate_signals(htf, ltf, "GC", cfg, bias=off) == []


def test_news_blackout():
    ltf = synthetic_5m()
    htf = resample(ltf, "1h")
    sigs = generate_signals(htf, ltf, "GC")
    t = sigs[0].time
    news = NewsCalendar(pd.DataFrame({"time_utc": [str(t)], "currency": ["USD"],
                                      "impact": ["high"], "event": ["CPI"]}),
                        pd.Timedelta(minutes=30), pd.Timedelta(minutes=30))
    sigs2 = generate_signals(htf, ltf, "GC", news=news)
    assert t not in [s.time for s in sigs2]


def _bars(rows, start="2026-01-05 10:00"):
    idx = pd.date_range(start, periods=len(rows), freq="5min", tz="UTC")
    return pd.DataFrame(rows, columns=["open", "high", "low", "close"], index=idx)


def _sig(t, direction="long", stop=95.0, target=110.0):
    return Signal("GC", direction, t, 100.0, stop, target, 2.0, "z", t, 96.0, "long", "londres")


def test_backtest_stop_first_when_both_hit():
    ltf = _bars([[100, 101, 99, 100], [100, 111, 94, 100], [100, 101, 99, 100]])
    trades = run_backtest(ltf, [_sig(ltf.index[1])])
    assert trades[0].outcome == "sl" and trades[0].r == pytest.approx(-1.0)


def test_backtest_tp_and_cost():
    ltf = _bars([[100, 101, 99, 100], [100, 111, 99, 110], [110, 111, 109, 110]])
    trades = run_backtest(ltf, [_sig(ltf.index[1])], cost=0.5)
    assert trades[0].outcome == "tp"
    assert trades[0].r == pytest.approx((10 - 0.5) / 5)
    assert summarize(trades)["trades"] == 1


def test_message_contains_levels():
    s = _sig(pd.Timestamp("2026-01-05 10:00", tz="UTC"))
    msg = format_signal(s, "GC1! CME")
    assert "ACHAT" in msg and "95.00" in msg and "110.00" in msg


def test_intraday_flat_and_window():
    from trade_analysis.backtest import IntradayRules
    rules = IntradayRules()
    # signal à 18:55 UTC, le prix ne touche ni stop ni objectif -> sortie fin de journée
    ltf = _bars([[100, 101, 99, 100]] * 30, start="2026-01-05 18:45")
    trades = run_backtest(ltf, [_sig(ltf.index[2])], intraday=rules)
    assert trades[0].outcome == "eod"
    assert trades[0].exit_time + pd.Timedelta(minutes=5) >= pd.Timestamp("2026-01-05 20:45", tz="UTC")
    # signal à 19:30 UTC : hors fenêtre d'entrée
    ltf2 = _bars([[100, 101, 99, 100]] * 5, start="2026-01-05 19:20")
    assert run_backtest(ltf2, [_sig(ltf2.index[2])], intraday=rules) == []


def test_intraday_daily_limits():
    from trade_analysis.backtest import IntradayRules
    rules = IntradayRules(max_loss_day_r=1.0)
    # chaque signal perd 1R (bougie qui touche le stop) -> 2e signal du jour refusé
    rows = [[100, 101, 99, 100], [100, 100.5, 94, 95]] * 6
    ltf = _bars(rows, start="2026-01-05 10:00")
    sigs = [_sig(ltf.index[1]), _sig(ltf.index[5])]
    trades = run_backtest(ltf, sigs, intraday=rules)
    assert len(trades) == 1 and trades[0].outcome == "sl"


def test_trend_filter_only_with_trend():
    from trade_analysis.strategy import htf_trend
    from trade_analysis.structure import find_pivots
    ltf = synthetic_5m()
    htf = resample(ltf, "1h")
    cfg = Config(htf_pivot_left=3, htf_pivot_right=3)
    sigs = generate_signals(htf, ltf, "GC", cfg)
    dur = pd.Timedelta(hours=1)
    tr = htf_trend(htf, find_pivots(htf, 3, 3, dur), dur, "structure")
    assert sigs
    for s in sigs:
        k = tr.index.searchsorted(s.time, side="right") - 1
        assert tr["bos"].iloc[k] == (1 if s.direction == "long" else -1)
    off = generate_signals(htf, ltf, "GC", Config(htf_pivot_left=3, htf_pivot_right=3, trend_mode="off"))
    assert len(off) >= len(sigs)


def test_direction_long_only():
    ltf = synthetic_5m()
    htf = resample(ltf, "1h")
    sigs = generate_signals(htf, ltf, "GC", Config(trend_mode="off", direction="long"))
    assert sigs and all(s.direction == "long" for s in sigs)


def test_separate_trend_timeframe():
    from trade_analysis.strategy import htf_trend
    from trade_analysis.structure import find_pivots
    ltf = synthetic_5m()
    zones, trend = resample(ltf, "1h"), resample(ltf, "4h")
    cfg = Config(htf_pivot_left=2, htf_pivot_right=2)
    sigs = generate_signals(zones, ltf, "GC", cfg, trend_htf=trend)
    dur = pd.Timedelta(hours=4)
    tr = htf_trend(trend, find_pivots(trend, 2, 2, dur), dur, "structure")
    for s in sigs:
        k = tr.index.searchsorted(s.time, side="right") - 1
        assert tr["bos"].iloc[k] == (1 if s.direction == "long" else -1)
