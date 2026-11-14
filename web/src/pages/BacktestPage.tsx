import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { BacktestResult, StopMode, StrategyOverrides, TargetMode, TrendMode } from '../api/types';
import { Card } from '../components/Card';
import { EquityChart } from '../components/EquityChart';
import { InstrumentSelect, useInstruments } from '../components/InstrumentSelect';
import { DirectionBadge } from '../components/SignalCard';
import { SmallSampleNotice } from '../components/SmallSampleNotice';
import { Breakdowns, SummaryGrid, rClass } from '../components/Stats';
import { ErrorMessage, EmptyState } from '../components/StateViews';
import { formatInt, formatNumber, plural, formatPrice, formatR, outcomeLabel, sessionLabel } from '../lib/format';
import { When } from '../lib/time';
import { useAction } from '../lib/useApi';

const TREND_LABELS: Record<TrendMode, string> = {
  structure: 'Structure H4 (cassure du dernier sommet / creux)',
  strict: 'Structure stricte',
  ema: 'Moyenne mobile (EMA)',
  off: 'Aucun filtre de tendance',
};
const TARGET_LABELS: Record<TargetMode, string> = {
  ob: "Order block opposé en H1",
  h4: 'Liquidité H4',
  rr: 'R:R fixe',
};
const STOP_LABELS: Record<StopMode, string> = {
  sweep: 'Derrière la mèche de liquidation',
  h4: 'Dernier creux / sommet H4',
};

interface StrategyForm {
  trendMode: '' | TrendMode;
  targetMode: '' | TargetMode;
  rr: string;
  stopMode: '' | StopMode;
  minTouches: string;
  minRr: string;
}

const EMPTY_STRATEGY: StrategyForm = { trendMode: '', targetMode: '', rr: '', stopMode: '', minTouches: '', minRr: '' };

/** Ne garde que les réglages modifiés : un champ vide = valeur par défaut de l'application. */
export function strategyOverrides(f: StrategyForm): StrategyOverrides {
  const o: StrategyOverrides = {};
  if (f.trendMode) o.trendMode = f.trendMode;
  if (f.targetMode) o.targetMode = f.targetMode;
  if (f.stopMode) o.stopMode = f.stopMode;
  const rr = Number.parseFloat(f.rr.replace(',', '.'));
  if (f.rr.trim() && Number.isFinite(rr)) o.rr = rr;
  const mt = Number.parseInt(f.minTouches, 10);
  if (f.minTouches.trim() && Number.isFinite(mt)) o.minTouches = mt;
  const mr = Number.parseFloat(f.minRr.replace(',', '.'));
  if (f.minRr.trim() && Number.isFinite(mr)) o.minRr = mr;
  return o;
}

function Results({ r }: { r: BacktestResult }) {
  const p = r.parameters;
  const st = p.strategy;
  return (
    <>
      <Card title={`Résultats — ${r.symbol} ${r.timeframe}`} className="card-wide">
        <p className="small">
          {formatInt(r.candles)} bougies du <When iso={r.firstCandle} /> au <When iso={r.lastCandle} /> ·{' '}
          {plural(r.signals.length, 'signal', 'signaux')} · {plural(r.trades.length, 'trade', 'trades')}.
        </p>
        <p className="small muted">
          Coût par trade : {formatNumber(p.cost, 2)} point · règles intraday : {p.intraday ? 'oui' : 'non'} · biais :{' '}
          {p.useBias ? 'utilisé' : 'ignoré'} · annonces : {p.useNews ? 'utilisées' : 'ignorées'} · tendance :{' '}
          {st.trendMode} · objectif : {st.targetMode} · stop : {st.stopMode} · contacts min. : {st.minTouches} · R:R
          min. : {formatNumber(st.minRr, 2)}
        </p>
        {r.summary.trades === 0 ? (
          <EmptyState title="Aucun trade sur cette période">
            Aucun signal n'a passé toutes les étapes. Essayez une période plus longue ou des réglages moins stricts.
          </EmptyState>
        ) : (
          <>
            <SmallSampleNotice trades={r.summary.trades} />
            <SummaryGrid summary={r.summary} />
            <EquityChart points={r.equityCurve} />
            <Breakdowns bySession={r.bySession} byDirection={r.byDirection} byOutcome={r.byOutcome} />
          </>
        )}
      </Card>
      {r.trades.length > 0 && (
        <Card title="Liste des trades" className="card-wide">
          <div className="table-wrap">
            <table className="table-trades">
              <thead>
                <tr>
                  <th>Entrée</th>
                  <th>Sens</th>
                  <th className="num">Prix d'entrée</th>
                  <th className="num">Stop</th>
                  <th className="num">Objectif</th>
                  <th>Sortie</th>
                  <th className="num">Prix de sortie</th>
                  <th>Type de sortie</th>
                  <th className="num">Résultat</th>
                  <th>Session</th>
                </tr>
              </thead>
              <tbody>
                {r.trades.map((t, i) => (
                  <tr key={`${t.entryTime}-${i}`}>
                    <td>
                      <When iso={t.entryTime} />
                    </td>
                    <td>
                      <DirectionBadge direction={t.direction} />
                    </td>
                    <td className="num">{formatPrice(t.entry)}</td>
                    <td className="num">{formatPrice(t.stop)}</td>
                    <td className="num">{formatPrice(t.target)}</td>
                    <td>
                      <When iso={t.exitTime} />
                    </td>
                    <td className="num">{formatPrice(t.exit)}</td>
                    <td>
                      {outcomeLabel(t.outcome)}
                      {t.openAtEnd && ' (encore ouvert en fin de fichier)'}
                    </td>
                    <td className={`num ${rClass(t.r)}`}>{formatR(t.r)}</td>
                    <td>{sessionLabel(t.session)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
}

export function BacktestPage() {
  const instruments = useInstruments();
  const [file, setFile] = useState<File | null>(null);
  const [symbol, setSymbol] = useState('');
  const [cost, setCost] = useState('');
  const [intraday, setIntraday] = useState(true);
  const [useBias, setUseBias] = useState(true);
  const [useNews, setUseNews] = useState(true);
  const [strategy, setStrategy] = useState<StrategyForm>(EMPTY_STRATEGY);
  const [result, setResult] = useState<BacktestResult | null>(null);
  const action = useAction(api.backtest);

  const set = <K extends keyof StrategyForm>(k: K, v: StrategyForm[K]) => setStrategy((s) => ({ ...s, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!file || !symbol) return;
    setResult(null);
    const c = Number.parseFloat(cost.replace(',', '.'));
    const r = await action.run({
      file,
      symbol,
      cost: cost.trim() && Number.isFinite(c) ? c : undefined,
      intraday,
      useBias,
      useNews,
      strategy: strategyOverrides(strategy),
    });
    if (r) setResult(r);
  };

  return (
    <>
      <h1>Backtest</h1>
      <p className="lead">
        Testez la stratégie sur un export CSV de TradingView (bougies 5 ou 15 minutes). Le calcul se fait en mémoire :
        rien n'est enregistré. Un backtest mesure le passé, il ne promet aucun gain.
      </p>
      <Card title="Lancer un backtest">
        <form className="form" onSubmit={submit}>
          <div className="field">
            <label htmlFor="bt-file">Fichier CSV TradingView</label>
            <input
              id="bt-file"
              type="file"
              accept=".csv,text/csv"
              required
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </div>
          <div className="form-cols">
            <div className="field">
              <label htmlFor="bt-symbol">Instrument</label>
              <InstrumentSelect id="bt-symbol" value={symbol} onChange={setSymbol} instruments={instruments.data} required />
            </div>
            <div className="field">
              <label htmlFor="bt-cost">Coût par trade (points de prix)</label>
              <input
                id="bt-cost"
                type="text"
                inputMode="decimal"
                value={cost}
                placeholder="vide = coût du paper trading"
                onChange={(e) => setCost(e.target.value)}
              />
            </div>
          </div>
          <label className="check">
            <input type="checkbox" checked={intraday} onChange={(e) => setIntraday(e.target.checked)} />
            Règles intraday (entrées 07:00–19:00 UTC, tout fermé à 20:45 UTC, 3 trades par jour, arrêt à −2 R)
          </label>
          <label className="check">
            <input type="checkbox" checked={useBias} onChange={(e) => setUseBias(e.target.checked)} />
            Utiliser le biais fondamental saisi
          </label>
          <label className="check">
            <input type="checkbox" checked={useNews} onChange={(e) => setUseNews(e.target.checked)} />
            Utiliser les annonces saisies (pas d'entrée 30 minutes avant et après)
          </label>

          <details className="settings">
            <summary>Réglages de la stratégie (facultatif)</summary>
            <p className="small muted">Laissez « par défaut » pour garder les réglages de l'application.</p>
            <div className="form-cols">
              <div className="field">
                <label htmlFor="bt-trend">Tendance</label>
                <select id="bt-trend" value={strategy.trendMode} onChange={(e) => set('trendMode', e.target.value as StrategyForm['trendMode'])}>
                  <option value="">Par défaut</option>
                  {(Object.keys(TREND_LABELS) as TrendMode[]).map((k) => (
                    <option key={k} value={k}>
                      {TREND_LABELS[k]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="bt-target">Objectif</label>
                <select id="bt-target" value={strategy.targetMode} onChange={(e) => set('targetMode', e.target.value as StrategyForm['targetMode'])}>
                  <option value="">Par défaut</option>
                  {(Object.keys(TARGET_LABELS) as TargetMode[]).map((k) => (
                    <option key={k} value={k}>
                      {TARGET_LABELS[k]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="bt-rr">R:R fixe (objectif R:R, ou faute d'order block)</label>
                <input id="bt-rr" type="text" inputMode="decimal" placeholder="par défaut : 2" value={strategy.rr} onChange={(e) => set('rr', e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="bt-stop">Stop</label>
                <select id="bt-stop" value={strategy.stopMode} onChange={(e) => set('stopMode', e.target.value as StrategyForm['stopMode'])}>
                  <option value="">Par défaut</option>
                  {(Object.keys(STOP_LABELS) as StopMode[]).map((k) => (
                    <option key={k} value={k}>
                      {STOP_LABELS[k]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="bt-touches">Contacts minimum d'une zone</label>
                <input id="bt-touches" type="number" min={1} step={1} placeholder="par défaut : 2" value={strategy.minTouches} onChange={(e) => set('minTouches', e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="bt-minrr">R:R minimum</label>
                <input id="bt-minrr" type="text" inputMode="decimal" placeholder="par défaut : 1" value={strategy.minRr} onChange={(e) => set('minRr', e.target.value)} />
              </div>
            </div>
          </details>

          <div className="btn-row">
            <button type="submit" className="btn btn-primary" disabled={!file || !symbol || action.pending}>
              {action.pending ? 'Calcul en cours…' : 'Lancer le backtest'}
            </button>
          </div>
        </form>
        <ErrorMessage error={action.error} title="Le backtest n'a pas abouti" />
      </Card>
      {result && <Results r={result} />}
    </>
  );
}
