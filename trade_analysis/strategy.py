"""Moteur de signaux : Tendance H4 -> Zone S/R (HTF) -> Liquidation (sweep LTF) -> Entrée (CHOCH LTF).

0. TENDANCE HTF (on ne trade que dans son sens) :
   - "structure" : haussière dès qu'une bougie HTF clôture au-dessus du dernier
     swing high confirmé (BOS/CHOCH), baissière dès qu'elle clôture sous le dernier swing low ;
   - "strict" : haussière si les 2 derniers sommets ET creux montent (HH+HL),
     baissière s'ils descendent (LH+LL), sinon range = pas de trade ;
   - "ema" : clôture LTF au-dessus / en dessous de la moyenne mobile HTF ;
   - "off" : pas de filtre.

Définitions objectives utilisées (toutes réglables dans `Config`) :

1. ZONE : pivots HTF (H1 ou H4) regroupés quand ils sont à moins de
   `zone_merge_atr` x ATR(HTF). Une zone compte si elle a >= `min_touches` contacts.

2. LIQUIDATION (sweep) sur LTF (5m ou 15m) :
   - haussière : la bougie précédente clôture AU-DESSUS du bas de la zone,
     la mèche descend sous la zone d'au moins `sweep_min_atr` x ATR(LTF),
     sans dépasser `sweep_max_atr` x ATR(HTF) (sinon c'est une cassure, pas un piège),
     et la bougie clôture de nouveau au-dessus du bas de la zone.
   - baissière : symétrique au-dessus du haut de la zone.

3. ENTRÉE : dans les `confirm_bars` bougies LTF suivant le sweep, une clôture
   casse le dernier swing high LTF confirmé (CHOCH haussier) — ou le dernier
   swing low pour une vente. Le signal est émis à la clôture de cette bougie ;
   en backtest l'entrée se fait à l'OUVERTURE de la bougie suivante.

4. RISQUE : stop sous la mèche du sweep - `sl_buffer_atr` x ATR(LTF).
   TP = `rr` x risque. Trade rejeté si le stop est plus large que
   `max_risk_htf_atr` x ATR(HTF).
"""

from __future__ import annotations

from dataclasses import asdict, dataclass

import pandas as pd

from .filters import BiasBook, NewsCalendar, session_of
from .structure import Pivot, ZoneBook, Zone, atr, find_pivots


@dataclass
class Config:
    # Zones HTF
    htf_pivot_left: int = 5
    htf_pivot_right: int = 5
    zone_merge_atr: float = 0.35
    zone_max_width_atr: float = 1.0
    min_touches: int = 2
    zone_max_age_bars: int = 300  # en bougies HTF
    # Sweep LTF
    sweep_min_atr: float = 0.2
    sweep_max_atr: float = 1.0
    # Confirmation LTF
    ltf_pivot_left: int = 3
    ltf_pivot_right: int = 2
    confirm_bars: int = 12
    # Risque
    sl_buffer_atr: float = 0.2
    rr: float = 2.0
    max_risk_htf_atr: float = 1.5
    # Filtres
    neutral_allows_both: bool = True
    trend_mode: str = "structure"  # "structure" | "strict" | "ema" | "off"
    trend_len: int = 50
    direction: str = "both"  # "both" | "long" | "short"
    sessions: tuple[str, ...] | None = None  # ex: ("londres", "overlap_londres_ny")
    atr_period: int = 14


@dataclass
class Signal:
    symbol: str
    direction: str  # "long" | "short"
    time: pd.Timestamp  # clôture de la bougie de confirmation
    entry: float  # prix de référence (clôture de confirmation)
    stop: float
    target: float
    rr: float
    zone: str
    sweep_time: pd.Timestamp
    sweep_extreme: float
    bias: str
    session: str
    next_zone: float | None = None

    @property
    def risk(self) -> float:
        return abs(self.entry - self.stop)

    def to_dict(self) -> dict:
        return asdict(self)


def htf_trend(htf: pd.DataFrame, pivots: list[Pivot], dur: pd.Timedelta,
              mode: str, ema_len: int = 50) -> pd.DataFrame:
    """Tendance HTF connue à la clôture de chaque bougie HTF (index = heure de clôture).

    Colonnes : `bos` (1/-1/0), `strict` (1/-1/0), `ema` (valeur de la moyenne).
    """
    closes = htf["close"].to_numpy()
    close_times = htf.index + dur
    last_h = prev_h = last_l = prev_l = None
    tr_bos = 0
    p_i = 0
    bos, strict = [], []
    for i in range(len(htf)):
        while p_i < len(pivots) and pivots[p_i].confirmed_at <= close_times[i]:
            p = pivots[p_i]
            if p.kind == "high":
                prev_h, last_h = last_h, p.price
            else:
                prev_l, last_l = last_l, p.price
            p_i += 1
        if last_h is not None and closes[i] > last_h:
            tr_bos = 1
        if last_l is not None and closes[i] < last_l:
            tr_bos = -1
        if None in (prev_h, prev_l):
            st = 0
        elif last_h > prev_h and last_l > prev_l:
            st = 1
        elif last_h < prev_h and last_l < prev_l:
            st = -1
        else:
            st = 0
        bos.append(tr_bos)
        strict.append(st)
    ema = htf["close"].ewm(span=ema_len, adjust=False).mean().to_numpy()
    return pd.DataFrame({"bos": bos, "strict": strict, "ema": ema}, index=close_times)


def _bar_duration(df: pd.DataFrame) -> pd.Timedelta:
    from .data import infer_bar_duration

    return infer_bar_duration(df)


def generate_signals(
    htf: pd.DataFrame,
    ltf: pd.DataFrame,
    symbol: str,
    cfg: Config | None = None,
    bias: BiasBook | None = None,
    news: NewsCalendar | None = None,
) -> list[Signal]:
    cfg = cfg or Config()
    bias = bias or BiasBook(None, symbol)
    news = news or NewsCalendar(None, pd.Timedelta(0), pd.Timedelta(0))

    htf_dur, ltf_dur = _bar_duration(htf), _bar_duration(ltf)
    htf_atr = atr(htf, cfg.atr_period)
    # ATR HTF connu seulement à la clôture de la bougie HTF.
    htf_atr_known = pd.Series(htf_atr.to_numpy(), index=htf.index + htf_dur).dropna()
    htf_atr_times, htf_atr_vals = htf_atr_known.index, htf_atr_known.to_numpy()
    ltf_atr = atr(ltf, cfg.atr_period)

    htf_pivots = find_pivots(htf, cfg.htf_pivot_left, cfg.htf_pivot_right, htf_dur)
    trend_df = htf_trend(htf, htf_pivots, htf_dur, cfg.trend_mode, cfg.trend_len)
    trend_times = trend_df.index
    trend_bos, trend_strict, trend_ema = (trend_df[k].to_numpy() for k in ("bos", "strict", "ema"))
    ltf_pivots = find_pivots(ltf, cfg.ltf_pivot_left, cfg.ltf_pivot_right, ltf_dur)

    book = ZoneBook(max_age=htf_dur * cfg.zone_max_age_bars)
    hp_i = lp_i = 0
    last_ltf_high: Pivot | None = None
    last_ltf_low: Pivot | None = None

    pending: dict | None = None  # sweep en attente de confirmation
    signals: list[Signal] = []

    o, h, l, c = (ltf[k].to_numpy() for k in ("open", "high", "low", "close"))
    times = ltf.index
    a_ltf = ltf_atr.to_numpy()

    for i in range(1, len(ltf)):
        close_time = times[i] + ltf_dur  # instant où la bougie i est connue

        # Dernier ATR HTF disponible à cet instant (bougie HTF clôturée)
        k = htf_atr_times.searchsorted(close_time, side="right") - 1
        a_htf = float(htf_atr_vals[k]) if k >= 0 else None

        # Pivots HTF confirmés -> zones
        if a_htf is not None:
            book.max_width = cfg.zone_max_width_atr * a_htf
        while hp_i < len(htf_pivots) and htf_pivots[hp_i].confirmed_at <= close_time:
            if a_htf is not None:
                book.add(htf_pivots[hp_i], cfg.zone_merge_atr * a_htf)
            hp_i += 1
        book.prune(close_time)

        # Pivots LTF confirmés -> structure courte
        while lp_i < len(ltf_pivots) and ltf_pivots[lp_i].confirmed_at <= close_time:
            p = ltf_pivots[lp_i]
            if p.kind == "high":
                last_ltf_high = p
            else:
                last_ltf_low = p
            lp_i += 1

        if a_htf is None or pd.isna(a_ltf[i]):
            continue

        # Tendance HTF en vigueur (dernière bougie HTF clôturée)
        kt = trend_times.searchsorted(close_time, side="right") - 1
        if cfg.trend_mode == "off" or kt < 0:
            trend = 0
        elif cfg.trend_mode == "structure":
            trend = int(trend_bos[kt])
        elif cfg.trend_mode == "strict":
            trend = int(trend_strict[kt])
        else:
            trend = 1 if c[i] > trend_ema[kt] else -1
        allow_long = (cfg.trend_mode == "off" or trend == 1) and cfg.direction != "short"
        allow_short = (cfg.trend_mode == "off" or trend == -1) and cfg.direction != "long"

        # 1) Confirmation d'un sweep en attente
        if pending is not None:
            if i - pending["bar"] > cfg.confirm_bars:
                pending = None
            else:
                d = pending["direction"]
                # le sweep est invalidé si le prix va au-delà de son extrême
                if (d == "long" and l[i] < pending["extreme"]) or (
                    d == "short" and h[i] > pending["extreme"]
                ):
                    pending = None
                elif (d == "long" and c[i] > pending["trigger"]) or (
                    d == "short" and c[i] < pending["trigger"]
                ):
                    ok_dir = allow_long if d == "long" else allow_short
                    sig = _build_signal(pending, i, c[i], a_ltf[i], a_htf, close_time,
                                        symbol, cfg, bias, news, book) if ok_dir else None
                    if sig is not None:
                        signals.append(sig)
                    pending = None
                    continue

        # 2) Détection d'un nouveau sweep
        if pending is None:
            pending = _detect_sweep(book.active(cfg.min_touches), i, h, l, c,
                                    a_ltf[i], a_htf, cfg, last_ltf_high, last_ltf_low,
                                    times[i], allow_long, allow_short)
    return signals


def _detect_sweep(zones: list[Zone], i, h, l, c, a_ltf, a_htf, cfg: Config,
                  last_high: Pivot | None, last_low: Pivot | None,
                  t: pd.Timestamp, allow_long: bool = True,
                  allow_short: bool = True) -> dict | None:
    best = None
    for z in zones:
        # Sweep haussier sous la zone
        pen = z.low - l[i]
        if (allow_long and c[i - 1] > z.low and pen >= cfg.sweep_min_atr * a_ltf
                and pen <= cfg.sweep_max_atr * a_htf and c[i] > z.low
                and last_high is not None and last_high.price > c[i]):
            cand = dict(direction="long", zone=z, extreme=l[i], bar=i, time=t,
                        trigger=last_high.price, touches=z.touches)
            if best is None or z.touches > best["touches"]:
                best = cand
        # Sweep baissier au-dessus de la zone
        pen = h[i] - z.high
        if (allow_short and c[i - 1] < z.high and pen >= cfg.sweep_min_atr * a_ltf
                and pen <= cfg.sweep_max_atr * a_htf and c[i] < z.high
                and last_low is not None and last_low.price < c[i]):
            cand = dict(direction="short", zone=z, extreme=h[i], bar=i, time=t,
                        trigger=last_low.price, touches=z.touches)
            if best is None or z.touches > best["touches"]:
                best = cand
    return best


def _build_signal(pending: dict, i: int, close: float, a_ltf: float, a_htf: float,
                  close_time: pd.Timestamp, symbol: str, cfg: Config,
                  bias: BiasBook, news: NewsCalendar, book: ZoneBook) -> Signal | None:
    d = pending["direction"]
    if not bias.allows(close_time, d, cfg.neutral_allows_both):
        return None
    if news.blocked(close_time):
        return None
    sess = session_of(close_time)
    if cfg.sessions and sess not in cfg.sessions:
        return None

    if d == "long":
        stop = pending["extreme"] - cfg.sl_buffer_atr * a_ltf
        risk = close - stop
        target = close + cfg.rr * risk
        opp = [z.low for z in book.active(cfg.min_touches) if z.low > close]
        next_zone = min(opp) if opp else None
    else:
        stop = pending["extreme"] + cfg.sl_buffer_atr * a_ltf
        risk = stop - close
        target = close - cfg.rr * risk
        opp = [z.high for z in book.active(cfg.min_touches) if z.high < close]
        next_zone = max(opp) if opp else None
    if risk <= 0 or risk > cfg.max_risk_htf_atr * a_htf:
        return None

    return Signal(
        symbol=symbol, direction=d, time=close_time, entry=float(close),
        stop=float(stop), target=float(target), rr=cfg.rr,
        zone=pending["zone"].describe(), sweep_time=pending["time"],
        sweep_extreme=float(pending["extreme"]), bias=bias.at(close_time),
        session=sess, next_zone=next_zone,
    )
