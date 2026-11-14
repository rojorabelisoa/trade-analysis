import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { PaperTradeStatus, ReplayResult } from '../api/types';
import { Card } from '../components/Card';
import { EquityChart } from '../components/EquityChart';
import { InstrumentSelect, useInstruments } from '../components/InstrumentSelect';
import { PaperTradeCard } from '../components/PaperTradeCard';
import { Pager } from '../components/Pager';
import { SmallSampleNotice } from '../components/SmallSampleNotice';
import { Breakdowns, SummaryGrid, rClass } from '../components/Stats';
import { EmptyState, ErrorMessage, Loading } from '../components/StateViews';
import { formatCurrency, formatInt, formatNumber, plural } from '../lib/format';
import { useAction, useApi } from '../lib/useApi';

const PAGE_SIZE = 20;

function ReplayReport({ result }: { result: ReplayResult }) {
  return (
    <div className="report" role="status">
      <p>
        <strong>Rejeu terminé pour {result.symbol}.</strong>
      </p>
      {result.timeframes.length === 0 && <p>Aucune bougie stockée pour cet instrument.</p>}
      <ul>
        {result.timeframes.map((t) =>
          t.skipped ? (
            <li key={t.timeframe}>
              {t.timeframe} : non rejoué ({t.skipped})
            </li>
          ) : (
            <li key={t.timeframe}>
              {t.timeframe} : {plural(t.signals, 'signal', 'signaux')}, {plural(t.trades, 'trade', 'trades')} dont{' '}
              {plural(t.open, 'ouvert', 'ouverts')}
              {t.excludedByHalt > 0 && `, ${plural(t.excludedByHalt, 'signal écarté', 'signaux écartés')} (coupe-circuit)`} —{' '}
              {t.inserted} nouveau(x), {t.updated} modifié(s), {t.deleted} supprimé(s), {t.unchanged} inchangé(s).
            </li>
          ),
        )}
      </ul>
    </div>
  );
}

export function PaperPage() {
  const instruments = useInstruments();
  const [symbol, setSymbol] = useState('');
  const [status, setStatus] = useState<'' | PaperTradeStatus>('');
  const [page, setPage] = useState(0);
  const [replaySymbol, setReplaySymbol] = useState('');
  const [replayResult, setReplayResult] = useState<ReplayResult | null>(null);

  const summary = useApi(() => api.paperSummary({ symbol: symbol || undefined }), [symbol]);
  const trades = useApi(
    () => api.paperTrades({ symbol: symbol || undefined, status: status || undefined, page, size: PAGE_SIZE }),
    [symbol, status, page],
  );
  const replay = useAction(api.replay);

  const doReplay = async () => {
    setReplayResult(null);
    const r = await replay.run(replaySymbol);
    if (r) {
      setReplayResult(r);
      summary.reload();
      trades.reload();
    }
  };

  const s = summary.data;

  return (
    <>
      <h1>Paper trading</h1>
      <p className="lead">
        Trades simulés à partir des signaux du moteur, avec les règles intraday (entrées 07:00–19:00 UTC, tout fermé à
        20:45 UTC, 3 trades par jour au plus). Aucun ordre réel n'est envoyé.
      </p>

      <div className="filters card">
        <div className="field">
          <label htmlFor="paper-symbol">Instrument</label>
          <InstrumentSelect
            id="paper-symbol"
            value={symbol}
            onChange={(v) => {
              setSymbol(v);
              setPage(0);
            }}
            instruments={instruments.data}
            allowAll
          />
        </div>
        <div className="field">
          <label htmlFor="paper-status">Trades</label>
          <select
            id="paper-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as '' | PaperTradeStatus);
              setPage(0);
            }}
          >
            <option value="">Ouverts et fermés</option>
            <option value="OPEN">Ouverts</option>
            <option value="CLOSED">Fermés</option>
          </select>
        </div>
      </div>

      <div className="grid">
        <Card title={`Résultats${symbol ? ` — ${symbol}` : ''}`} className="card-wide">
          {summary.loading && <Loading />}
          <ErrorMessage error={summary.error} onRetry={summary.reload} />
          {s && s.closedTrades === 0 && s.openTrades === 0 && (
            <EmptyState title="Aucun trade simulé">
              Importez des bougies (écran <Link to="/donnees">Données</Link>) ou lancez un rejeu ci-dessous.
            </EmptyState>
          )}
          {s && (s.closedTrades > 0 || s.openTrades > 0) && (
            <>
              <SmallSampleNotice trades={s.summary.trades} />
              <SummaryGrid
                summary={s.summary}
                extra={[
                  { label: 'Trades ouverts', value: formatInt(s.openTrades) },
                  {
                    label: 'Résultat en devise (trades fermés)',
                    value: formatCurrency(s.pnlCurrency),
                    className: rClass(s.pnlCurrency),
                  },
                ]}
              />
              <p className="muted small">
                Taille des positions : capital fixe de {formatCurrency(s.capital, false)}, risque de{' '}
                {formatNumber(s.riskPercent, 1)} % par trade. Les trades ouverts ne comptent pas dans ces chiffres.
              </p>
              <EquityChart points={s.equityCurve} />
              <Breakdowns bySession={s.bySession} byDirection={s.byDirection} byOutcome={s.byOutcome} />
            </>
          )}
        </Card>

        <Card title="Rejouer le paper trading">
          <p className="small muted">
            Le rejeu recalcule tous les trades simulés d'un instrument à partir des bougies et des signaux stockés. Il est
            fait automatiquement après chaque import ; utile après un changement du coupe-circuit.
          </p>
          <form
            className="form-row"
            onSubmit={(e) => {
              e.preventDefault();
              if (replaySymbol) void doReplay();
            }}
          >
            <div className="field">
              <label htmlFor="replay-symbol">Instrument</label>
              <InstrumentSelect
                id="replay-symbol"
                value={replaySymbol}
                onChange={setReplaySymbol}
                instruments={instruments.data}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary" disabled={!replaySymbol || replay.pending}>
              {replay.pending ? 'Rejeu en cours…' : 'Lancer le rejeu'}
            </button>
          </form>
          <ErrorMessage error={replay.error} title="Le rejeu n'a pas abouti" />
          {replayResult && <ReplayReport result={replayResult} />}
        </Card>
      </div>

      <h2 className="section-title">Trades simulés</h2>
      {trades.loading && <Loading />}
      <ErrorMessage error={trades.error} onRetry={trades.reload} />
      {trades.data && !trades.loading && (
        <>
          <p className="muted small">
            {trades.data.total} trade{trades.data.total > 1 ? 's' : ''}, du plus récent au plus ancien.
          </p>
          {trades.data.items.length === 0 ? (
            <EmptyState title="Aucun trade avec ces filtres">
              Changez les filtres, ou importez des bougies dans l'écran <Link to="/donnees">Données</Link>.
            </EmptyState>
          ) : (
            <div className="items items-grid">
              {trades.data.items.map((t) => (
                <PaperTradeCard key={t.id} trade={t} />
              ))}
            </div>
          )}
          <Pager page={page} size={PAGE_SIZE} total={trades.data.total} onPage={setPage} />
        </>
      )}
    </>
  );
}
