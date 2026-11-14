import { describe, expect, it } from 'vitest';
import type { BiasEntry } from '../api/types';
import {
  currentBias,
  formatCurrency,
  formatDateTime,
  formatDay,
  formatDuration,
  formatNumber,
  formatPct,
  formatPrice,
  formatR,
  formatTime,
  parseExits,
  plural,
} from '../lib/format';

// Intl fr-FR sépare les milliers par une espace fine insécable : on normalise pour comparer.
const n = (s: string) => s.replace(/[  ]/g, ' ');

describe('nombres', () => {
  it('formate les nombres à la française', () => {
    expect(n(formatNumber(1234.5, 2))).toBe('1 234,50');
    expect(formatNumber(null)).toBe('—');
    expect(formatNumber(Number.NaN)).toBe('—');
  });

  it('formate les résultats en R avec leur signe', () => {
    expect(formatR(1.254)).toBe('+1,25 R');
    expect(formatR(-1.0803)).toBe('−1,08 R');
    expect(formatR(0)).toBe('0,00 R');
    expect(formatR(-0.001)).toBe('0,00 R');
    expect(formatR(-0.906, 3)).toBe('−0,906 R');
    expect(formatR(null)).toBe('—');
  });

  it('formate pourcentages, devises et prix', () => {
    expect(formatPct(8.3)).toBe('8,3 %');
    expect(n(formatCurrency(-8706.13))).toBe('−8 706 $');
    expect(n(formatCurrency(1500))).toBe('+1 500 $');
    expect(n(formatCurrency(100000, false))).toBe('100 000 $');
    expect(n(formatPrice(1995.6622748884279))).toBe('1 995,66');
    expect(formatPrice(4.53215)).toBe('4,5322');
  });

  it('formate les durées', () => {
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(175)).toBe('2 h 55 min');
    expect(formatDuration(180)).toBe('3 h');
    expect(formatDuration(null)).toBe('—');
  });

  it('accorde le pluriel', () => {
    expect(plural(1, 'signal', 'signaux')).toBe('1 signal');
    expect(plural(0, 'signal', 'signaux')).toBe('0 signal');
    expect(plural(33, 'signal', 'signaux')).toBe('33 signaux');
  });

  it('lit les sorties objectif / stop / fin de journée', () => {
    expect(parseExits('0/11/1')).toEqual({ tp: 0, sl: 11, eod: 1 });
    expect(parseExits('bad')).toBeNull();
  });
});

describe('heures', () => {
  const iso = '2026-01-31T20:50:00Z';

  it('affiche les heures dans le fuseau du navigateur', () => {
    expect(formatDateTime(iso, 'local', 'Europe/Paris')).toBe('31/01/2026 21:50');
    expect(formatDateTime('2026-07-01T06:05:00Z', 'local', 'Europe/Paris')).toBe('01/07/2026 08:05');
    expect(formatDateTime(iso, 'local', 'America/New_York')).toBe('31/01/2026 15:50');
    expect(formatTime(iso, 'local', 'Europe/Paris')).toBe('21:50');
  });

  it('affiche la mention UTC en mode UTC', () => {
    expect(formatDateTime(iso, 'utc')).toBe('31/01/2026 20:50 UTC');
    expect(formatTime(iso, 'utc')).toBe('20:50 UTC');
  });

  it('gère les valeurs absentes ou invalides', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('pas une date')).toBe('—');
    expect(formatDay('2026-01-05')).toBe('05/01/2026');
  });
});

describe('biais en vigueur', () => {
  const e = (id: number, effectiveDate: string, bias: BiasEntry['bias'], symbol = 'GC'): BiasEntry => ({
    id,
    effectiveDate,
    symbol,
    bias,
    note: null,
  });
  const entries = [e(1, '2026-01-01', 'long'), e(2, '2026-02-01', 'short'), e(3, '2026-03-01', 'off'), e(4, '2026-01-10', 'short', 'SI')];

  it('prend la dernière date passée (même règle que le moteur)', () => {
    const r = currentBias(entries, 'GC', '2026-02-15');
    expect(r.bias).toBe('short');
    expect(r.entry?.id).toBe(2);
    expect(r.next?.id).toBe(3);
  });

  it("s'applique dès la date d'effet", () => {
    expect(currentBias(entries, 'GC', '2026-02-01').bias).toBe('short');
  });

  it('vaut off avant la première date et neutre sans aucune saisie', () => {
    expect(currentBias(entries, 'GC', '2025-12-31').bias).toBe('off');
    expect(currentBias(entries, 'CL', '2026-02-15').bias).toBe('neutral');
  });
});
