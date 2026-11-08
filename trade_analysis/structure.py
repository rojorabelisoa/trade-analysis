"""Briques objectives : ATR, swings (pivots) sans look-ahead, zones S/R.

Règle anti look-ahead : un swing high à la bougie i n'est CONNU qu'une fois les
`right` bougies suivantes clôturées. On lui attache donc un `confirmed_at` =
heure de clôture de la bougie i+right. Rien ne peut l'utiliser avant.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd


def atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
    prev_close = df["close"].shift(1)
    tr = pd.concat(
        [
            df["high"] - df["low"],
            (df["high"] - prev_close).abs(),
            (df["low"] - prev_close).abs(),
        ],
        axis=1,
    ).max(axis=1)
    return tr.ewm(alpha=1 / period, adjust=False, min_periods=period).mean()


@dataclass(frozen=True)
class Pivot:
    kind: str  # "high" | "low"
    price: float
    time: pd.Timestamp  # ouverture de la bougie du pivot
    confirmed_at: pd.Timestamp  # clôture de la bougie i+right


def find_pivots(
    df: pd.DataFrame, left: int, right: int, bar_duration: pd.Timedelta
) -> list[Pivot]:
    """Swing high : high strictement supérieur aux `left` bougies avant et
    supérieur ou égal aux `right` bougies après (idem pour les lows)."""
    highs = df["high"].to_numpy()
    lows = df["low"].to_numpy()
    times = df.index
    pivots: list[Pivot] = []
    for i in range(left, len(df) - right):
        h = highs[i]
        if h > highs[i - left : i].max() and h >= highs[i + 1 : i + 1 + right].max():
            pivots.append(Pivot("high", h, times[i], times[i + right] + bar_duration))
        lo = lows[i]
        if lo < lows[i - left : i].min() and lo <= lows[i + 1 : i + 1 + right].min():
            pivots.append(Pivot("low", lo, times[i], times[i + right] + bar_duration))
    pivots.sort(key=lambda p: p.confirmed_at)
    return pivots


@dataclass
class Zone:
    low: float
    high: float
    touches: int
    first_time: pd.Timestamp
    last_time: pd.Timestamp
    kinds: set[str] = field(default_factory=set)

    @property
    def mid(self) -> float:
        return (self.low + self.high) / 2

    def describe(self) -> str:
        origin = "/".join(sorted(self.kinds))
        return f"{self.low:.2f}-{self.high:.2f} ({self.touches} contacts, pivots {origin})"


class ZoneBook:
    """Zones S/R construites au fil de l'eau à partir des pivots HTF confirmés.

    Un nouveau pivot à moins de `merge_tol` d'une zone existante l'élargit et
    ajoute un contact ; sinon il crée une zone. Les zones trop anciennes
    (`max_age`) sont oubliées.
    """

    def __init__(self, max_age: pd.Timedelta, max_width: float | None = None):
        self.zones: list[Zone] = []
        self.max_age = max_age
        self.max_width = max_width

    def add(self, pivot: Pivot, merge_tol: float) -> None:
        best, best_dist = None, np.inf
        for z in self.zones:
            if z.low - merge_tol <= pivot.price <= z.high + merge_tol:
                d = abs(pivot.price - z.mid)
                if d < best_dist:
                    best, best_dist = z, d
        if best is not None:
            new_low, new_high = min(best.low, pivot.price), max(best.high, pivot.price)
            if self.max_width is None or new_high - new_low <= self.max_width:
                best.low, best.high = new_low, new_high
            best.touches += 1
            best.last_time = max(best.last_time, pivot.time)
            best.kinds.add(pivot.kind)
            return
        self.zones.append(
            Zone(pivot.price, pivot.price, 1, pivot.time, pivot.time, {pivot.kind})
        )

    def prune(self, now: pd.Timestamp) -> None:
        self.zones = [z for z in self.zones if now - z.last_time <= self.max_age]

    def active(self, min_touches: int) -> list[Zone]:
        return [z for z in self.zones if z.touches >= min_touches]
