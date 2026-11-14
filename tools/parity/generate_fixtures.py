"""Génère les fixtures de parité Java <-> Python.

Le moteur Python de la branche `develop` est la référence. Ce script le lance sur
plusieurs scénarios et écrit, pour chacun, les signaux et les trades attendus.
Le moteur Java doit reproduire ces fichiers (tests de parité du module `engine`).

Usage (depuis la racine de `bot-trading`) :
    git worktree add ../reference origin/develop
    python3 tools/parity/generate_fixtures.py ../reference

Sortie : engine/src/test/resources/parity/
"""

from __future__ import annotations

import json
import shutil
import sys
from dataclasses import asdict
from pathlib import Path

import numpy as np
import pandas as pd

REF = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(REF))

from trade_analysis.backtest import IntradayRules, run_backtest, summarize  # noqa: E402
from trade_analysis.data import load_tradingview_csv, resample  # noqa: E402
from trade_analysis.filters import BiasBook, NewsCalendar  # noqa: E402
from trade_analysis.strategy import Config, find_order_blocks, generate_signals, htf_trend  # noqa: E402
from trade_analysis.structure import atr, find_pivots  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "engine/src/test/resources/parity"


def iso(ts) -> str:
    return pd.Timestamp(ts).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_candles(df: pd.DataFrame, path: Path) -> None:
    out = df[["open", "high", "low", "close", "volume"]].copy()
    out.insert(0, "time", (df.index - pd.Timestamp("1970-01-01", tz="UTC")) // pd.Timedelta("1s"))
    out.rename(columns={"volume": "Volume"}).to_csv(path, index=False, float_format="%.17g")


def trending_15m(n=9000, seed=7, start="2026-02-02") -> pd.DataFrame:
    """Marché avec tendances lentes (sinusoïde) + bruit : la tendance H4 change de sens."""
    rng = np.random.default_rng(seed)
    t = np.arange(n)
    base = 3000 + 60 * np.sin(2 * np.pi * t / 1900) + 25 * np.sin(2 * np.pi * t / 430)
    noise = np.zeros(n)
    for i in range(1, n):
        noise[i] = 0.9 * noise[i - 1] + rng.normal(0, 1.6)
    px = np.round(base + noise, 2)
    idx = pd.date_range(start, periods=n, freq="15min", tz="UTC")
    o = np.concatenate([[px[0]], px[:-1]])
    wick = np.round(np.abs(rng.normal(0, 1.1, (2, n))), 2)
    return pd.DataFrame({
        "open": o, "close": px,
        "high": np.maximum(o, px) + wick[0], "low": np.minimum(o, px) - wick[1],
        "volume": 1.0,
    }, index=pd.DatetimeIndex(idx, name="time"))


def sig_dict(s) -> dict:
    d = asdict(s)
    d["time"], d["sweep_time"] = iso(s.time), iso(s.sweep_time)
    return d


def trade_dict(t) -> dict:
    return {"signal_time": iso(t.signal.time), "entry_time": iso(t.entry_time), "entry": t.entry,
            "exit_time": iso(t.exit_time), "exit": t.exit, "outcome": t.outcome, "r": t.r}


def clean(x):
    """JSON strict : inf / nan -> chaîne."""
    if isinstance(x, dict):
        return {k: clean(v) for k, v in x.items()}
    if isinstance(x, (np.floating, float)):
        x = float(x)
        return x if np.isfinite(x) else ("inf" if x > 0 else "-inf" if x < 0 else "nan")
    if isinstance(x, np.integer):
        return int(x)
    return x


SCENARIOS = [
    # nom, série, règle zones, règle tendance, config, biais, annonces, sessions
    dict(name="s1_defaut_h4_h1_5m", ltf="demo_5m", zone_rule="1h", trend_rule="4h", cfg={}),
    dict(name="s2_sans_tendance_rr_fixe", ltf="demo_5m", zone_rule="1h", trend_rule=None,
         cfg=dict(trend_mode="off", target_mode="rr", stop_mode="sweep")),
    dict(name="s3_strict_stop_objectif_h4", ltf="trend_15m", zone_rule="1h", trend_rule="4h",
         cfg=dict(trend_mode="strict", stop_mode="h4", target_mode="h4", min_rr=1.0,
                  htf_pivot_left=3, htf_pivot_right=3, max_risk_htf_atr=2.5)),
    dict(name="s4_ema_long_biais_annonces", ltf="demo_5m", zone_rule="1h", trend_rule="4h",
         cfg=dict(trend_mode="ema", trend_len=20, direction="long", neutral_allows_both=False,
                  sessions=("londres", "overlap_londres_ny", "new_york")),
         bias=True, news=True),
    dict(name="s5_tendances_15m_defaut", ltf="trend_15m", zone_rule="1h", trend_rule="4h", cfg={}),
    dict(name="s6_zones_h4_pivots_3_1_contact", ltf="trend_15m", zone_rule="4h", trend_rule=None,
         cfg=dict(htf_pivot_left=3, htf_pivot_right=3, min_touches=1, trend_mode="off",
                  target_mode="h4", stop_mode="h4", min_rr=0.5, max_risk_htf_atr=3.0)),
    dict(name="s7_biais_neutre_autorise", ltf="demo_5m", zone_rule="1h", trend_rule=None,
         cfg=dict(trend_mode="off"), bias=True, news=True),
]


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "candles").mkdir(parents=True)
    (OUT / "inputs").mkdir()

    series = {
        "demo_5m": load_tradingview_csv(str(REF / "examples/demo_5m.csv")),
        "trend_15m": trending_15m(),
    }
    for name in list(series):
        # La série de référence est celle RELUE depuis le CSV : la fixture se suffit à elle-même.
        write_candles(series[name], OUT / "candles" / f"{name}.csv")
        series[name] = load_tradingview_csv(str(OUT / "candles" / f"{name}.csv"))
    for name, df in series.items():
        for rule in ("1h", "4h"):
            write_candles(resample(df, rule), OUT / "candles" / f"{name}__{rule}.csv")
    shutil.copy(REF / "examples/bias.csv", OUT / "inputs/bias.csv")
    shutil.copy(REF / "examples/news.csv", OUT / "inputs/news.csv")

    # Briques intermédiaires (pour localiser un écart de parité)
    blocks = {}
    for name, df in series.items():
        h1, h4 = resample(df, "1h"), resample(df, "4h")
        d1, d4 = pd.Timedelta(hours=1), pd.Timedelta(hours=4)
        piv = find_pivots(h1, 5, 5, d1)
        tr = htf_trend(h4, find_pivots(h4, 5, 5, d4), d4, "structure")
        blocks[name] = {
            "atr14_ltf_tail": [None if pd.isna(v) else float(v) for v in atr(df, 14).iloc[[13, 14, 100, -1]]],
            "atr14_h1_at": {"index": [13, 14, 100, len(h1) - 1],
                            "values": [None if pd.isna(v) else float(v) for v in atr(h1, 14).iloc[[13, 14, 100, -1]]]},
            "h1_pivots_5_5": [{"kind": p.kind, "price": float(p.price), "time": iso(p.time),
                               "confirmed_at": iso(p.confirmed_at)} for p in piv],
            "h1_order_blocks": [{"dir": o["dir"], "top": float(o["top"]), "bot": float(o["bot"]),
                                 "known_at": iso(o["known_at"])} for o in find_order_blocks(h1, d1)],
            "h4_trend_structure": [{"close_time": iso(t), "bos": int(r.bos), "strict": int(r.strict),
                                    "ema": float(r.ema),
                                    "last_h": None if pd.isna(r.last_h) else float(r.last_h),
                                    "last_l": None if pd.isna(r.last_l) else float(r.last_l),
                                    "hh": float(r.hh), "ll": float(r.ll),
                                    "atr": None if pd.isna(r.atr) else float(r.atr)}
                                   for t, r in tr.iterrows()],
        }
    (OUT / "blocks.json").write_text(json.dumps(clean(blocks), indent=1))

    index = []
    for sc in SCENARIOS:
        ltf = series[sc["ltf"]]
        zones = resample(ltf, sc["zone_rule"])
        trend = resample(ltf, sc["trend_rule"]) if sc["trend_rule"] else None
        cfg = Config(**sc["cfg"])
        bias = BiasBook.from_csv(str(REF / "examples/bias.csv") if sc.get("bias") else None, "GC")
        news = NewsCalendar.from_csv(str(REF / "examples/news.csv") if sc.get("news") else None, 30, 30)
        signals = generate_signals(zones, ltf, "GC", cfg, bias, news, trend_htf=trend)
        backtests = []
        for intraday in (False, True):
            trades = run_backtest(ltf, signals, cost=0.3, max_hold_bars=288,
                                  intraday=IntradayRules() if intraday else None)
            backtests.append({"intraday": intraday, "cost": 0.3, "max_hold_bars": 288,
                              "trades": [trade_dict(t) for t in trades], "summary": summarize(trades)})
        cfg_d = asdict(cfg)
        cfg_d["sessions"] = list(cfg.sessions) if cfg.sessions else None
        doc = {
            "name": sc["name"], "symbol": "GC", "ltf": sc["ltf"],
            "zone_rule": sc["zone_rule"], "trend_rule": sc["trend_rule"],
            "use_bias": bool(sc.get("bias")), "use_news": bool(sc.get("news")),
            "news_before_min": 30, "news_after_min": 30,
            "config": cfg_d, "signals": [sig_dict(s) for s in signals], "backtests": backtests,
        }
        (OUT / f"{sc['name']}.json").write_text(json.dumps(clean(doc), indent=1))
        index.append(sc["name"])
        print(f"{sc['name']:38s} signaux={len(signals):3d} "
              f"trades={len(backtests[0]['trades']):3d} / intraday={len(backtests[1]['trades']):3d}")
    (OUT / "index.json").write_text(json.dumps(index, indent=1))


if __name__ == "__main__":
    main()
