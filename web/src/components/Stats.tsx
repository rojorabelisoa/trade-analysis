import type { BreakdownRow, SummaryView } from '../api/types';
import {
  breakdownKeyLabel,
  formatDuration,
  formatInt,
  formatNumber,
  formatPct,
  formatR,
  parseExits,
} from '../lib/format';

export function rClass(v: number | null | undefined): string {
  if (v === null || v === undefined || !Number.isFinite(v) || Math.abs(v) < 0.005) return '';
  return v > 0 ? 'pos' : 'neg';
}

function Stat({ label, value, className = '', hint }: { label: string; value: string; className?: string; hint?: string }) {
  return (
    <div className="stat">
      <dt>{label}</dt>
      <dd className={className} title={hint}>
        {value}
      </dd>
    </div>
  );
}

/** Résumé des statistiques (moteur) : mêmes indicateurs pour le paper trading et le backtest. */
export function SummaryGrid({ summary, extra }: { summary: SummaryView; extra?: { label: string; value: string; className?: string }[] }) {
  const exits = parseExits(summary.exitsTpSlEod);
  return (
    <dl className="stats">
      <Stat label="Trades terminés" value={formatInt(summary.trades)} />
      <Stat label="Réussite" value={formatPct(summary.winRatePct)} />
      <Stat
        label="Espérance par trade"
        value={formatR(summary.expectancyR, 3)}
        className={rClass(summary.expectancyR)}
        hint="Gain ou perte moyen par trade, en R (1 R = le risque pris jusqu'au stop)"
      />
      <Stat label="Total" value={formatR(summary.totalR)} className={rClass(summary.totalR)} />
      <Stat
        label="Facteur de profit"
        value={summary.noLosses ? 'aucune perte' : formatNumber(summary.profitFactor, 2)}
        hint="Somme des gains / somme des pertes"
      />
      <Stat
        label="Plus forte baisse"
        value={formatR(summary.maxDrawdownR)}
        className={rClass(summary.maxDrawdownR)}
        hint="Plus grande baisse de la courbe cumulée depuis un sommet (drawdown)"
      />
      <Stat label="Pire série de pertes" value={formatInt(summary.worstLosingStreak)} />
      <Stat
        label="Sorties objectif / stop / fin de journée"
        value={exits ? `${exits.tp} / ${exits.sl} / ${exits.eod}` : summary.exitsTpSlEod}
      />
      <Stat label="Durée moyenne" value={formatDuration(summary.avgDurationMin)} />
      {extra?.map((e) => <Stat key={e.label} label={e.label} value={e.value} className={e.className} />)}
    </dl>
  );
}

function BreakdownTable({ title, kind, rows }: { title: string; kind: 'session' | 'direction' | 'outcome'; rows: BreakdownRow[] }) {
  return (
    <div className="breakdown">
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="muted small">Aucun trade.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{title.replace('Par ', '').replace(/^./, (c) => c.toUpperCase())}</th>
                <th className="num">Trades</th>
                <th className="num">Réussite</th>
                <th className="num">Espérance</th>
                <th className="num">Total</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.key}>
                  <td>{breakdownKeyLabel(kind, r.key)}</td>
                  <td className="num">{r.trades}</td>
                  <td className="num">{formatPct(r.winRatePct)}</td>
                  <td className={`num ${rClass(r.expectancyR)}`}>{formatR(r.expectancyR, 3)}</td>
                  <td className={`num ${rClass(r.totalR)}`}>{formatR(r.totalR)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export function Breakdowns({
  bySession,
  byDirection,
  byOutcome,
}: {
  bySession: BreakdownRow[];
  byDirection: BreakdownRow[];
  byOutcome: BreakdownRow[];
}) {
  return (
    <div className="breakdowns">
      <BreakdownTable title="Par session" kind="session" rows={bySession} />
      <BreakdownTable title="Par sens" kind="direction" rows={byDirection} />
      <BreakdownTable title="Par type de sortie" kind="outcome" rows={byOutcome} />
    </div>
  );
}
