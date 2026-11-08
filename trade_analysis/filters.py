"""Filtres : biais fondamental, annonces économiques, sessions.

Le fondamental vient de ta lecture d'investing.com. Le script ne devine rien :
tu notes ton biais dans `bias.csv`, et les annonces à fort impact dans
`news.csv`. Le script applique ensuite ces décisions de façon systématique.

bias.csv (le biais s'applique à partir de `date` jusqu'à la ligne suivante) :
    date,symbol,bias,note
    2026-11-02,GC,long,Fed dovish + DXY faible
    2026-11-06,GC,neutral,attente CPI

news.csv (heures en UTC ; sur investing.com règle le fuseau sur GMT) :
    time_utc,currency,impact,event
    2026-11-07 13:30,USD,high,Non-Farm Payrolls
"""

from __future__ import annotations

import pandas as pd

VALID_BIAS = {"long", "short", "neutral", "off"}

# Heures UTC approximatives (celles des notes, hors heure d'été).
SESSIONS = {
    "asie": (0, 7),
    "londres": (7, 12),
    "overlap_londres_ny": (12, 16),
    "new_york": (16, 21),
    "fermeture": (21, 24),
}


def session_of(ts: pd.Timestamp) -> str:
    h = ts.hour
    for name, (start, end) in SESSIONS.items():
        if start <= h < end:
            return name
    return "inconnue"


class BiasBook:
    def __init__(self, df: pd.DataFrame | None, symbol: str):
        self.symbol = symbol
        if df is None or df.empty:
            self.series = None
            return
        df = df.copy()
        df.columns = [c.strip().lower() for c in df.columns]
        df = df[df["symbol"].astype(str).str.upper() == symbol.upper()]
        df["bias"] = df["bias"].str.strip().str.lower()
        bad = set(df["bias"]) - VALID_BIAS
        if bad:
            raise ValueError(f"Biais inconnus dans bias.csv: {bad} (attendu: {VALID_BIAS})")
        df["date"] = pd.to_datetime(df["date"], utc=True)
        self.series = df.sort_values("date").set_index("date")["bias"]

    @classmethod
    def from_csv(cls, path: str | None, symbol: str) -> "BiasBook":
        return cls(pd.read_csv(path) if path else None, symbol)

    def at(self, ts: pd.Timestamp) -> str:
        """Biais en vigueur. Sans fichier de biais : 'neutral'."""
        if self.series is None:
            return "neutral"
        s = self.series[self.series.index <= ts]
        return s.iloc[-1] if len(s) else "off"

    def allows(self, ts: pd.Timestamp, direction: str, neutral_allows_both: bool) -> bool:
        b = self.at(ts)
        if b == "off":
            return False
        if b == "neutral":
            return neutral_allows_both
        return b == direction


class NewsCalendar:
    def __init__(self, df: pd.DataFrame | None, before: pd.Timedelta, after: pd.Timedelta,
                 impacts: tuple[str, ...] = ("high",)):
        self.before, self.after = before, after
        if df is None or df.empty:
            self.times = pd.DatetimeIndex([], tz="UTC")
            self.events: list[str] = []
            return
        df = df.copy()
        df.columns = [c.strip().lower() for c in df.columns]
        df = df[df["impact"].str.strip().str.lower().isin(impacts)]
        df = df.assign(t=pd.to_datetime(df["time_utc"], utc=True)).sort_values("t")
        self.times = pd.DatetimeIndex(df["t"])
        self.events = df["event"].astype(str).tolist()

    @classmethod
    def from_csv(cls, path: str | None, before_min: int, after_min: int) -> "NewsCalendar":
        return cls(pd.read_csv(path) if path else None,
                   pd.Timedelta(minutes=before_min), pd.Timedelta(minutes=after_min))

    def blocked(self, ts: pd.Timestamp) -> str | None:
        """Nom de l'annonce si `ts` tombe dans la fenêtre d'exclusion."""
        for t, ev in zip(self.times, self.events):
            if t - self.before <= ts <= t + self.after:
                return ev
        return None

    def upcoming(self, ts: pd.Timestamp, horizon: pd.Timedelta) -> list[str]:
        return [f"{t:%Y-%m-%d %H:%M} UTC {ev}" for t, ev in zip(self.times, self.events)
                if ts <= t <= ts + horizon]
