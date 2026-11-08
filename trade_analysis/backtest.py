"""Backtest prudent des signaux.

Hypothèses (volontairement pessimistes) :
- entrée à l'OUVERTURE de la bougie qui suit le signal (pas au prix idéal) ;
- stop et objectif gardent leur prix ; le risque est recalculé depuis l'entrée réelle ;
- si stop ET objectif sont touchés dans la même bougie -> on compte le STOP ;
- un seul trade ouvert à la fois ;
- `cost` = spread + commission + slippage en unités de prix, retiré à chaque trade ;
- sortie forcée à la clôture après `max_hold_bars` bougies.
Les résultats sont exprimés en R (multiples du risque).
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

import numpy as np
import pandas as pd

from .strategy import Signal


@dataclass
class IntradayRules:
    """Scalping / intraday : pas de position la nuit, limites journalières (heures UTC)."""

    entry_start_h: int = 7
    entry_end_h: int = 19
    flat_h: int = 20
    flat_m: int = 45
    max_trades_day: int = 3
    max_loss_day_r: float = 2.0

    def entry_allowed(self, ts: pd.Timestamp) -> bool:
        m = ts.hour * 60 + ts.minute
        return self.entry_start_h * 60 <= m < self.entry_end_h * 60

    def must_flat(self, ts: pd.Timestamp) -> bool:
        m = ts.hour * 60 + ts.minute
        return m >= self.flat_h * 60 + self.flat_m or m < self.entry_start_h * 60


@dataclass
class Trade:
    signal: Signal
    entry_time: pd.Timestamp
    entry: float
    exit_time: pd.Timestamp
    exit: float
    outcome: str  # "tp" | "sl" | "timeout" | "eod"
    r: float


def run_backtest(ltf: pd.DataFrame, signals: list[Signal], cost: float = 0.0,
                 max_hold_bars: int = 288,
                 intraday: IntradayRules | None = None) -> list[Trade]:
    times = ltf.index
    bar = times[1] - times[0] if len(times) > 1 else pd.Timedelta(0)
    trades_per_day: dict = defaultdict(int)
    r_per_day: dict = defaultdict(float)
    o, h, l, c = (ltf[k].to_numpy() for k in ("open", "high", "low", "close"))
    trades: list[Trade] = []
    busy_until = pd.Timestamp.min.tz_localize("UTC")

    for s in sorted(signals, key=lambda x: x.time):
        if s.time < busy_until:
            continue
        day = s.time.date()
        if intraday is not None and not (
            intraday.entry_allowed(s.time)
            and trades_per_day[day] < intraday.max_trades_day
            and r_per_day[day] > -intraday.max_loss_day_r
        ):
            continue
        i = times.searchsorted(s.time, side="left")  # bougie qui ouvre au signal
        if i >= len(times):
            continue
        entry = o[i]
        long = s.direction == "long"
        risk = (entry - s.stop) if long else (s.stop - entry)
        if risk <= 0:  # gap au-delà du stop : trade annulé
            continue
        outcome, exit_px, j = "timeout", c[min(i + max_hold_bars, len(c) - 1)], None
        for j in range(i, min(i + max_hold_bars + 1, len(c))):
            hit_sl = l[j] <= s.stop if long else h[j] >= s.stop
            hit_tp = h[j] >= s.target if long else l[j] <= s.target
            if hit_sl:
                outcome, exit_px = "sl", s.stop
                break
            if hit_tp:
                outcome, exit_px = "tp", s.target
                break
            if intraday is not None and intraday.must_flat(times[j] + bar):
                outcome, exit_px = "eod", c[j]
                break
        else:
            j = min(i + max_hold_bars, len(c) - 1)
        pnl = (exit_px - entry) if long else (entry - exit_px)
        r = (pnl - cost) / risk
        exit_time = times[j]
        trades.append(Trade(s, times[i], float(entry), exit_time, float(exit_px), outcome, float(r)))
        trades_per_day[day] += 1
        r_per_day[day] += r
        busy_until = exit_time
    return trades


def summarize(trades: list[Trade]) -> dict:
    if not trades:
        return {"trades": 0}
    r = np.array([t.r for t in trades])
    equity = r.cumsum()
    dd = equity - np.maximum.accumulate(np.concatenate([[0.0], equity]))[1:]
    gains, losses = r[r > 0].sum(), -r[r < 0].sum()
    return {
        "trades": len(r),
        "taux_reussite_%": round(100 * (r > 0).mean(), 1),
        "esperance_R": round(r.mean(), 3),
        "total_R": round(r.sum(), 2),
        "profit_factor": round(gains / losses, 2) if losses > 0 else float("inf"),
        "drawdown_max_R": round(dd.min(), 2),
        "pire_serie_pertes": _worst_streak(r),
        "sorties_tp_sl_fin": "/".join(str(sum(t.outcome == o for t in trades))
                                     for o in ("tp", "sl", "eod")),
        "duree_moyenne_min": round(np.mean([(t.exit_time - t.entry_time).total_seconds() / 60
                                            for t in trades]), 0),
    }


def _worst_streak(r: np.ndarray) -> int:
    worst = cur = 0
    for x in r:
        cur = cur + 1 if x <= 0 else 0
        worst = max(worst, cur)
    return worst


def trades_frame(trades: list[Trade]) -> pd.DataFrame:
    rows = []
    for t in trades:
        s = t.signal
        rows.append({
            "signal_time": s.time, "entry_time": t.entry_time, "direction": s.direction,
            "entry": t.entry, "stop": s.stop, "target": s.target, "exit_time": t.exit_time,
            "exit": t.exit, "outcome": t.outcome, "r": round(t.r, 3), "session": s.session,
            "bias": s.bias, "zone": s.zone,
        })
    return pd.DataFrame(rows)


def breakdown(trades: list[Trade], key: str) -> pd.DataFrame:
    df = trades_frame(trades)
    if df.empty:
        return df
    g = df.groupby(key)["r"]
    return pd.DataFrame({
        "trades": g.size(),
        "taux_reussite_%": g.apply(lambda s: round(100 * (s > 0).mean(), 1)),
        "esperance_R": g.mean().round(3),
        "total_R": g.sum().round(2),
    })
