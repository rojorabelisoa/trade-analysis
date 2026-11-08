"""Chargement des données OHLCV exportées depuis TradingView (CME).

TradingView > menu du graphique > « Exporter les données du graphique » produit
un CSV avec les colonnes : time, open, high, low, close, Volume (+ indicateurs).
`time` est l'heure d'OUVERTURE de la bougie, en timestamp Unix ou en ISO 8601.
Toutes les heures sont converties en UTC.
"""

from __future__ import annotations

import pandas as pd

REQUIRED = ["open", "high", "low", "close"]


def load_tradingview_csv(path: str) -> pd.DataFrame:
    df = pd.read_csv(path)
    df.columns = [c.strip().lower() for c in df.columns]
    if "time" not in df.columns:
        raise ValueError(f"{path}: colonne 'time' absente (colonnes: {list(df.columns)})")
    missing = [c for c in REQUIRED if c not in df.columns]
    if missing:
        raise ValueError(f"{path}: colonnes manquantes {missing}")

    t = df["time"]
    if pd.api.types.is_numeric_dtype(t):
        idx = pd.to_datetime(t, unit="s", utc=True)
    else:
        idx = pd.to_datetime(t, utc=True)
    out = df[REQUIRED + (["volume"] if "volume" in df.columns else [])].copy()
    out.index = pd.DatetimeIndex(idx, name="time")
    out = out[~out.index.duplicated(keep="last")].sort_index()
    out = out.dropna(subset=REQUIRED)
    if "volume" not in out.columns:
        out["volume"] = 0.0
    return out.astype(float)


def infer_bar_duration(df: pd.DataFrame) -> pd.Timedelta:
    """Durée d'une bougie = écart le plus fréquent entre deux horodatages."""
    diffs = df.index.to_series().diff().dropna()
    if diffs.empty:
        raise ValueError("Pas assez de bougies pour déduire le timeframe")
    return diffs.mode().iloc[0]


def resample(df: pd.DataFrame, rule: str) -> pd.DataFrame:
    """Construit un timeframe supérieur (ex: '1h', '4h') à partir d'un inférieur.

    Utile si tu n'exportes que le 5m/15m. Les bougies sont étiquetées par leur
    heure d'ouverture, comme sur TradingView.
    """
    agg = {"open": "first", "high": "max", "low": "min", "close": "last", "volume": "sum"}
    out = df.resample(rule, label="left", closed="left").agg(agg)
    return out.dropna(subset=["open"])
