"""Mise en forme d'un signal (texte prêt à copier dans Telegram/WhatsApp)."""

from __future__ import annotations

import pandas as pd

from .filters import NewsCalendar
from .strategy import Signal


def format_signal(s: Signal, instrument_note: str = "", news: NewsCalendar | None = None,
                  decimals: int = 2) -> str:
    f = f"{{:.{decimals}f}}"
    arrow = "ACHAT 🟢" if s.direction == "long" else "VENTE 🔴"
    lines = [
        f"📡 SIGNAL {s.symbol} — {arrow}",
        f"Instrument : {s.symbol}{(' (' + instrument_note + ')') if instrument_note else ''}",
        f"Émis : {s.time:%Y-%m-%d %H:%M} UTC (session {s.session})",
        f"Entrée réf. : {f.format(s.entry)}",
        f"Stop loss  : {f.format(s.stop)}  (risque {f.format(s.risk)})",
        f"Objectif   : {f.format(s.target)}  (R:R 1:{s.rr:g})",
    ]
    if s.next_zone is not None:
        lines.append(f"Prochaine zone opposée : {f.format(s.next_zone)}")
        lo, hi = sorted((s.entry, s.target))
        if lo < s.next_zone < hi:
            lines.append("⚠️ Une zone opposée se trouve AVANT l'objectif : prise partielle conseillée.")
    lines += [
        "",
        "Raison :",
        f"• Zone S/R HTF : {s.zone}",
        f"• Liquidation : mèche à {f.format(s.sweep_extreme)} ({s.sweep_time:%H:%M} UTC) puis réintégration",
        "• Entrée : cassure de structure LTF (CHOCH) en clôture",
        f"• Biais fondamental : {s.bias}",
    ]
    if news is not None:
        up = news.upcoming(s.time, pd.Timedelta(hours=24))
        if up:
            lines += ["", "⚠️ Annonces dans les 24h :"] + [f"• {u}" for u in up]
    lines += ["", "Invalidation : clôture au-delà du stop. Pas de conseil financier."]
    return "\n".join(lines)
