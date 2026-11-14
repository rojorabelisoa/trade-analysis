import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { TradingStatus } from '../api/types';
import { Card } from '../components/Card';
import { EquityChart } from '../components/EquityChart';
import { useInstruments } from '../components/InstrumentSelect';
import { KillSwitch } from '../components/KillSwitch';
import { PaperTradeCard } from '../components/PaperTradeCard';
import { SignalCard } from '../components/SignalCard';
import { SmallSampleNotice } from '../components/SmallSampleNotice';
import { SummaryGrid, rClass } from '../components/Stats';
import { EmptyState, ErrorMessage, Loading } from '../components/StateViews';
import { biasShort, currentBias, formatCurrency, formatDay, impactLabel } from '../lib/format';
import { When } from '../lib/time';
import { useApi } from '../lib/useApi';

function KillSwitchCard() {
  const status = useApi(() => api.tradingStatus(), []);
  const [local, setLocal] = useState<TradingStatus | undefined>(undefined);
  useEffect(() => setLocal(status.data), [status.data]);
  return (
    <Card title="Coupe-circuit" className="card-ks">
      {status.loading && !local && <Loading />}
      <ErrorMessage error={status.error} onRetry={status.reload} />
      {local && <KillSwitch status={local} onChange={setLocal} />}
      {local && (
        <dl className="kv kv-mode">
          <div>
            <dt>Mode</dt>
            <dd>{local.paperTrading ? 'Paper trading (simulation)' : 'Paper trading désactivé'}</dd>
          </div>
          <div>
            <dt>Trading réel</dt>
            <dd>{local.liveTradingEnabled ? 'Activé' : 'Désactivé'}</dd>
          </div>
        </dl>
      )}
    </Card>
  );
}

function BiasCard() {
  const instruments = useInstruments();
  const biases = useApi(() => api.biases(), []);
  const error = instruments.error ?? biases.error;
  return (
    <Card title="Biais en vigueur" action={<Link to="/fondamental">Modifier</Link>}>
      {(instruments.loading || biases.loading) && <Loading />}
      <ErrorMessage
        error={error}
        onRetry={() => {
          instruments.reload();
          biases.reload();
        }}
      />
      {instruments.data && biases.data && (
        <ul className="bias-list">
          {instruments.data.map((i) => {
            const b = currentBias(biases.data!, i.symbol);
            return (
              <li key={i.symbol}>
                <span className="bias-sym" title={i.name}>
                  {i.symbol}
                </span>
                <span className={`badge bias-${b.bias}`}>{biasShort(b.bias)}</span>
                <span className="muted small">
                  {b.explanation}
                  {b.next && ` · ${biasShort(b.next.bias)} à partir du ${formatDay(b.next.effectiveDate)}`}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

function NextNewsCard() {
  const [from] = useState(() => new Date().toISOString());
  const news = useApi(() => api.news({ from }), [from]);
  const next = news.data?.find((n) => n.impact === 'high');
  return (
    <Card title="Prochaine annonce à fort impact" action={<Link to="/fondamental">Annonces</Link>}>
      {news.loading && <Loading />}
      <ErrorMessage error={news.error} onRetry={news.reload} />
      {news.data && !next && (
        <EmptyState title="Aucune annonce à fort impact à venir">
          Saisissez les annonces dans l'écran <Link to="/fondamental">Fondamental</Link> : le bot n'ouvre aucun trade
          30 minutes avant et après.
        </EmptyState>
      )}
      {next && (
        <div className="news-next">
          <p className="news-when">
            <When iso={next.time} />
          </p>
          <p>
            <strong>{next.currency}</strong> · {next.event}{' '}
            <span className="badge impact-high">Impact {impactLabel(next.impact).toLowerCase()}</span>
          </p>
          <p className="muted small">Aucune entrée 30 minutes avant et après.</p>
        </div>
      )}
    </Card>
  );
}

function LatestSignalsCard() {
  const signals = useApi(() => api.signals({ size: 5 }), []);
  return (
    <Card title="Derniers signaux" className="card-wide" action={<Link to="/signaux">Tout voir</Link>}>
      {signals.loading && <Loading />}
      <ErrorMessage error={signals.error} onRetry={signals.reload} />
      {signals.data && signals.data.items.length === 0 && (
        <EmptyState title="Aucun signal pour l'instant">
          Importez des bougies dans l'écran <Link to="/donnees">Données</Link> : les signaux sont calculés
          automatiquement. Les alertes TradingView apparaissent aussi ici.
        </EmptyState>
      )}
      {signals.data && signals.data.items.length > 0 && (
        <div className="items">
          {signals.data.items.map((s) => (
            <SignalCard key={s.id} signal={s} />
          ))}
        </div>
      )}
    </Card>
  );
}

function OpenPositionsCard() {
  const trades = useApi(() => api.paperTrades({ status: 'OPEN', size: 20 }), []);
  return (
    <Card title="Positions simulées ouvertes" action={<Link to="/paper">Paper trading</Link>}>
      {trades.loading && <Loading />}
      <ErrorMessage error={trades.error} onRetry={trades.reload} />
      {trades.data && trades.data.items.length === 0 && (
        <EmptyState title="Aucune position ouverte">
          Une position s'ouvre quand un signal du moteur passe les règles du paper trading.
        </EmptyState>
      )}
      {trades.data && trades.data.items.length > 0 && (
        <div className="items">
          {trades.data.items.map((t) => (
            <PaperTradeCard key={t.id} trade={t} />
          ))}
        </div>
      )}
    </Card>
  );
}

function PaperSummaryCard() {
  const summary = useApi(() => api.paperSummary(), []);
  const d = summary.data;
  return (
    <Card title="Résultats du paper trading" className="card-wide" action={<Link to="/paper">Détail</Link>}>
      {summary.loading && <Loading />}
      <ErrorMessage error={summary.error} onRetry={summary.reload} />
      {d && d.closedTrades === 0 && d.openTrades === 0 && (
        <EmptyState title="Aucun trade simulé">
          Importez des bougies (écran <Link to="/donnees">Données</Link>) : le paper trading rejoue les signaux du
          moteur automatiquement.
        </EmptyState>
      )}
      {d && (d.closedTrades > 0 || d.openTrades > 0) && (
        <>
          <SmallSampleNotice trades={d.summary.trades} />
          <SummaryGrid
            summary={d.summary}
            extra={[
              { label: 'Trades ouverts', value: String(d.openTrades) },
              { label: 'Résultat en devise', value: formatCurrency(d.pnlCurrency), className: rClass(d.pnlCurrency) },
            ]}
          />
          <EquityChart points={d.equityCurve} />
        </>
      )}
    </Card>
  );
}

export function DashboardPage() {
  return (
    <>
      <h1>Tableau de bord</h1>
      <div className="grid">
        <KillSwitchCard />
        <BiasCard />
        <NextNewsCard />
        <OpenPositionsCard />
        <PaperSummaryCard />
        <LatestSignalsCard />
      </div>
    </>
  );
}
