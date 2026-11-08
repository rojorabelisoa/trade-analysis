"""Ligne de commande.

Exemples :
  # Backtest H1 contexte / 5m entrée, H1 reconstruit depuis le CSV 5m
  python -m trade_analysis backtest --ltf data/GC1_5m.csv --preset h1-5m --symbol GC \
      --bias bias.csv --news news.csv --cost 0.3

  # Derniers signaux, avec CSV H4 exporté séparément
  python -m trade_analysis scan --ltf data/GC1_15m.csv --htf data/GC1_4h.csv --symbol GC
"""

from __future__ import annotations

import argparse
import sys

import pandas as pd

from .backtest import IntradayRules, breakdown, run_backtest, summarize, trades_frame
from .data import load_tradingview_csv, resample
from .filters import BiasBook, NewsCalendar
from .message import format_signal
from .strategy import Config, generate_signals

# preset -> (timeframe des zones, timeframe de la tendance)
PRESETS = {"h4-h1-15m": ("1h", "4h"), "h4-h1-5m": ("1h", "4h"),
           "h1-5m": ("1h", "1h"), "h4-15m": ("4h", "4h")}


def _load(args) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame | None]:
    ltf = load_tradingview_csv(args.ltf)
    zone_rule, trend_rule = PRESETS.get(args.preset, (None, None)) if args.preset else (args.htf_rule, None)
    if args.htf:
        htf = load_tradingview_csv(args.htf)
    else:
        if not zone_rule:
            sys.exit("Donne --htf (CSV) ou --preset h4-h1-15m|h4-h1-5m|h1-5m|h4-15m ou --htf-rule 1h")
        htf = resample(ltf, zone_rule)
    trend_rule = args.trend_rule or trend_rule
    trend = resample(ltf, trend_rule) if trend_rule and trend_rule != zone_rule else None
    return htf, ltf, trend


def _config(args) -> Config:
    cfg = Config(rr=args.rr, min_touches=args.min_touches,
                 neutral_allows_both=not args.neutral_blocks,
                 trend_mode=args.trend, direction=args.direction,
                 stop_mode=args.stop, target_mode=args.target, min_rr=args.min_rr)
    if args.sessions:
        cfg.sessions = tuple(s.strip() for s in args.sessions.split(","))
    return cfg


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="trade_analysis")
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("backtest", "scan"):
        sp = sub.add_parser(name)
        sp.add_argument("--ltf", required=True, help="CSV TradingView timeframe d'entrée (5m/15m)")
        sp.add_argument("--htf", help="CSV TradingView timeframe de contexte (H1/H4)")
        sp.add_argument("--preset", choices=PRESETS, help="reconstruit le HTF depuis le LTF")
        sp.add_argument("--htf-rule", help="timeframe des zones S/R reconstruit depuis le LTF, ex: 1h")
        sp.add_argument("--trend-rule", help="timeframe de la tendance reconstruit depuis le LTF, ex: 4h")
        sp.add_argument("--symbol", default="GC")
        sp.add_argument("--bias", help="bias.csv (ton biais fondamental)")
        sp.add_argument("--news", help="news.csv (annonces fort impact, UTC)")
        sp.add_argument("--news-before", type=int, default=30, help="minutes bloquées avant")
        sp.add_argument("--news-after", type=int, default=30, help="minutes bloquées après")
        sp.add_argument("--rr", type=float, default=2.0)
        sp.add_argument("--min-touches", type=int, default=2)
        sp.add_argument("--sessions", help="ex: londres,overlap_londres_ny")
        sp.add_argument("--trend", choices=["structure", "strict", "ema", "off"], default="structure",
                        help="tendance HTF : on ne trade que dans son sens (défaut : structure BOS/CHOCH)")
        sp.add_argument("--direction", choices=["both", "long", "short"], default="both")
        sp.add_argument("--stop", choices=["h4", "sweep"], default="h4",
                        help="stop : dernier creux/sommet du TF de tendance (h4) ou mèche de liquidation (sweep)")
        sp.add_argument("--target", choices=["h4", "rr"], default="h4",
                        help="objectif : liquidité du TF de tendance (h4) ou R:R fixe (rr, voir --rr)")
        sp.add_argument("--min-rr", type=float, default=1.5)
        sp.add_argument("--neutral-blocks", action="store_true",
                        help="biais 'neutral' = pas de trade (défaut : les deux sens)")
        sp.add_argument("--decimals", type=int, default=2)
        if name == "backtest":
            sp.add_argument("--cost", type=float, default=0.0,
                            help="spread+commission+slippage par trade, en prix")
            sp.add_argument("--max-hold", type=int, default=288, help="bougies LTF max en position")
            sp.add_argument("--out", help="exporter les trades en CSV")
            sp.add_argument("--intraday", action="store_true",
                            help="pas de position la nuit : entrées 7h-19h UTC, clôture 20h45, 3 trades et -2R max par jour")
        else:
            sp.add_argument("--last", type=int, default=1, help="nombre de signaux à afficher")
            sp.add_argument("--note", default="", help="ex: 'GC1! CME, contrat déc.'")

    pi = sub.add_parser("pine-inputs", help="convertit bias.csv / news.csv pour le Pine Script")
    pi.add_argument("--symbol", default="GC")
    pi.add_argument("--bias")
    pi.add_argument("--news")

    args = p.parse_args(argv)
    if args.cmd == "pine-inputs":
        if args.bias:
            b = pd.read_csv(args.bias)
            b.columns = [c.strip().lower() for c in b.columns]
            b = b[b["symbol"].astype(str).str.upper() == args.symbol.upper()].sort_values("date")
            print("Biais fondamental :")
            print(";".join(f"{pd.Timestamp(d):%Y-%m-%d}:{v.strip().lower()}"
                           for d, v in zip(b["date"], b["bias"])))
        if args.news:
            n = pd.read_csv(args.news)
            n.columns = [c.strip().lower() for c in n.columns]
            n = n[n["impact"].str.strip().str.lower() == "high"]
            print("\nAnnonces fort impact (UTC) :")
            print("\n".join(f"{pd.Timestamp(t):%Y-%m-%d %H:%M}" for t in sorted(n["time_utc"])))
        return 0

    htf, ltf, trend_htf = _load(args)
    cfg = _config(args)
    bias = BiasBook.from_csv(args.bias, args.symbol)
    news = NewsCalendar.from_csv(args.news, args.news_before, args.news_after)
    signals = generate_signals(htf, ltf, args.symbol, cfg, bias, news, trend_htf=trend_htf)

    print(f"Données : {len(ltf)} bougies LTF, {len(htf)} bougies HTF, "
          f"{ltf.index[0]:%Y-%m-%d} -> {ltf.index[-1]:%Y-%m-%d}")
    print(f"Signaux détectés : {len(signals)}\n")

    if args.cmd == "scan":
        for s in signals[-args.last:]:
            print(format_signal(s, args.note, news, args.decimals))
            print("-" * 40)
        return 0

    trades = run_backtest(ltf, signals, cost=args.cost, max_hold_bars=args.max_hold,
                          intraday=IntradayRules() if args.intraday else None)
    for k, v in summarize(trades).items():
        print(f"{k:>20} : {v}")
    if trades:
        print("\nPar session :\n", breakdown(trades, "session").to_string())
        print("\nPar sens :\n", breakdown(trades, "direction").to_string())
        if len(trades) < 100:
            print(f"\n⚠️  Seulement {len(trades)} trades : échantillon trop petit pour conclure.")
    if args.out:
        trades_frame(trades).to_csv(args.out, index=False)
        print(f"\nTrades exportés : {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
