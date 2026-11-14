// Mise en forme pour l'affichage (français). Aucune valeur affichée ne doit dépendre de
// l'endroit où le calcul est fait : les heures arrivent en UTC et sont converties ici.

import type { BiasEntry, BiasValue } from '../api/types';

export const DASH = '—';

/** Fuseau d'affichage : celui du navigateur ("local") ou UTC. */
export type TimeMode = 'local' | 'utc';

const isNum = (v: number | null | undefined): v is number =>
  typeof v === 'number' && Number.isFinite(v);

export function formatNumber(v: number | null | undefined, digits = 2): string {
  if (!isNum(v)) return DASH;
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(v);
}

export function formatInt(v: number | null | undefined): string {
  if (!isNum(v)) return DASH;
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(v);
}

/** Prix : 2 décimales au-dessus de 100 (or, pétrole), 4 en dessous (cuivre, argent). */
export function formatPrice(v: number | null | undefined): string {
  if (!isNum(v)) return DASH;
  const digits = Math.abs(v) >= 100 ? 2 : 4;
  return formatNumber(v, digits);
}

/** Résultat en R, signé : "+1,25 R", "−1,08 R". */
export function formatR(v: number | null | undefined, digits = 2): string {
  if (!isNum(v)) return DASH;
  const rounded = Number(v.toFixed(digits));
  const sign = rounded > 0 ? '+' : rounded < 0 ? '−' : '';
  return `${sign}${formatNumber(Math.abs(rounded), digits)} R`;
}

/** Pourcentage déjà exprimé en % (ex. 8.3 -> "8,3 %"). */
export function formatPct(v: number | null | undefined, digits = 1): string {
  if (!isNum(v)) return DASH;
  return `${formatNumber(v, digits)} %`;
}

/** Montant en dollars, signé, sans décimales : "−8 706 $". */
export function formatCurrency(v: number | null | undefined, signed = true): string {
  if (!isNum(v)) return DASH;
  const rounded = Math.round(v);
  const sign = signed ? (rounded > 0 ? '+' : rounded < 0 ? '−' : '') : rounded < 0 ? '−' : '';
  return `${sign}${formatInt(Math.abs(rounded))} $`;
}

/** Ratio R:R : "2,00". */
export function formatRr(v: number | null | undefined): string {
  return formatNumber(v, 2);
}

/** Durée en minutes : "45 min", "2 h 55 min", "3 h". */
export function formatDuration(minutes: number | null | undefined): string {
  if (!isNum(minutes)) return DASH;
  const m = Math.round(minutes);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rest = m % 60;
  return rest === 0 ? `${h} h` : `${h} h ${String(rest).padStart(2, '0')} min`;
}

/** Fuseau du navigateur (ex. "Europe/Paris"). */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'local';
  } catch {
    return 'local';
  }
}

const dtfCache = new Map<string, Intl.DateTimeFormat>();

function dtf(mode: TimeMode, withDate: boolean, tz?: string): Intl.DateTimeFormat {
  const key = `${mode}|${withDate}|${tz ?? ''}`;
  let f = dtfCache.get(key);
  if (!f) {
    f = new Intl.DateTimeFormat('fr-FR', {
      ...(withDate ? { day: '2-digit', month: '2-digit', year: 'numeric' } : {}),
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZone: mode === 'utc' ? 'UTC' : tz,
    });
    dtfCache.set(key, f);
  }
  return f;
}

/**
 * Date et heure d'un instant ISO : "31/01/2026 21:50" dans le fuseau du navigateur,
 * ou "31/01/2026 20:50 UTC". {@code tz} sert aux tests (fuseau imposé).
 */
export function formatDateTime(iso: string | null | undefined, mode: TimeMode = 'local', tz?: string): string {
  if (!iso) return DASH;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return DASH;
  const s = dtf(mode, true, tz).format(d).replace(',', '');
  return mode === 'utc' ? `${s} UTC` : s;
}

/** Heure seule : "21:50" (ou "20:50 UTC"). */
export function formatTime(iso: string | null | undefined, mode: TimeMode = 'local', tz?: string): string {
  if (!iso) return DASH;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return DASH;
  const s = dtf(mode, false, tz).format(d);
  return mode === 'utc' ? `${s} UTC` : s;
}

/** Date seule "AAAA-MM-JJ" (date d'effet d'un biais) -> "01/01/2026". Pas de conversion de fuseau. */
export function formatDay(day: string | null | undefined): string {
  if (!day) return DASH;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : day;
}

/** Date UTC du jour "AAAA-MM-JJ". */
export function todayUtc(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}

// ---------------------------------------------------------------------------
// Libellés en mots simples
// ---------------------------------------------------------------------------

export function directionLabel(d: string | null | undefined): string {
  if (d === 'long') return 'Achat (long)';
  if (d === 'short') return 'Vente (short)';
  return d ?? DASH;
}

export function outcomeLabel(o: string | null | undefined): string {
  switch (o) {
    case 'tp':
      return 'Objectif atteint';
    case 'sl':
      return 'Stop touché';
    case 'eod':
      return 'Fin de journée';
    case 'timeout':
      return 'Durée maximale';
    default:
      return o ?? DASH;
  }
}

export function sessionLabel(s: string | null | undefined): string {
  switch (s) {
    case 'asie':
      return 'Asie';
    case 'londres':
      return 'Londres';
    case 'overlap_londres_ny':
      return 'Londres + New York';
    case 'new_york':
      return 'New York';
    case 'fermeture':
      return 'Fermeture';
    default:
      return s ?? DASH;
  }
}

export function biasLabel(b: string | null | undefined): string {
  switch (b) {
    case 'long':
      return 'Long (achats seulement)';
    case 'short':
      return 'Short (ventes seulement)';
    case 'neutral':
      return 'Neutre (les deux sens)';
    case 'off':
      return 'Off (aucun trade)';
    default:
      return b ?? DASH;
  }
}

export function biasShort(b: string | null | undefined): string {
  switch (b) {
    case 'long':
      return 'Long';
    case 'short':
      return 'Short';
    case 'neutral':
      return 'Neutre';
    case 'off':
      return 'Off';
    default:
      return b ?? DASH;
  }
}

export function impactLabel(i: string | null | undefined): string {
  switch (i) {
    case 'high':
      return 'Fort';
    case 'medium':
      return 'Moyen';
    case 'low':
      return 'Faible';
    default:
      return i ?? DASH;
  }
}

export function sourceLabel(s: string | null | undefined): string {
  if (s === 'ENGINE') return 'Moteur';
  if (s === 'TRADINGVIEW') return 'TradingView';
  return s ?? DASH;
}

export function notificationKindLabel(k: string): string {
  switch (k) {
    case 'SIGNAL':
      return 'Signal';
    case 'TRADE_CLOSED':
      return 'Trade simulé terminé';
    case 'HALT':
      return 'Coupe-circuit activé';
    case 'RESUME':
      return 'Reprise';
    default:
      return k;
  }
}

export function notificationStatusLabel(s: string): string {
  switch (s) {
    case 'SENT':
      return 'Envoyée';
    case 'FAILED':
      return 'Échec';
    case 'SKIPPED':
      return 'Non envoyée';
    case 'PENDING':
      return 'En cours';
    default:
      return s;
  }
}

export function breakdownKeyLabel(kind: 'session' | 'direction' | 'outcome', key: string): string {
  if (kind === 'session') return sessionLabel(key);
  if (kind === 'direction') return directionLabel(key);
  return outcomeLabel(key);
}

/** "0/11/1" (objectif / stop / fin de journée) -> nombres. */
export function parseExits(s: string | null | undefined): { tp: number; sl: number; eod: number } | null {
  if (!s) return null;
  const parts = s.split('/').map((x) => Number.parseInt(x, 10));
  if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return null;
  return { tp: parts[0]!, sl: parts[1]!, eod: parts[2]! };
}

/** "1 signal", "3 signaux" (nombre formaté à la française). */
export function plural(n: number, singular: string, pluralForm: string): string {
  return `${formatInt(n)} ${Math.abs(n) > 1 ? pluralForm : singular}`;
}

/** En dessous de ce nombre de trades, un rappel signale que l'échantillon est trop petit. */
export const SMALL_SAMPLE = 100;

/**
 * Biais en vigueur aujourd'hui pour un instrument, avec la même règle que le moteur :
 * aucune ligne pour l'instrument -> neutre ; avant la première date -> off ; sinon la
 * dernière ligne dont la date d'effet (minuit UTC) est passée.
 */
export function currentBias(
  entries: BiasEntry[],
  symbol: string,
  today: string = todayUtc(),
): { bias: BiasValue; entry: BiasEntry | null; next: BiasEntry | null; explanation: string } {
  const own = entries
    .filter((e) => e.symbol === symbol)
    .sort((a, b) => a.effectiveDate.localeCompare(b.effectiveDate));
  if (own.length === 0) {
    return { bias: 'neutral', entry: null, next: null, explanation: 'Aucun biais saisi : les deux sens sont permis.' };
  }
  let current: BiasEntry | null = null;
  let next: BiasEntry | null = null;
  for (const e of own) {
    if (e.effectiveDate <= today) current = e;
    else if (!next) next = e;
  }
  if (!current) {
    return {
      bias: 'off',
      entry: null,
      next,
      explanation: 'Avant la première date saisie : aucun trade.',
    };
  }
  return { bias: current.bias, entry: current, next, explanation: `Depuis le ${formatDay(current.effectiveDate)}` };
}
