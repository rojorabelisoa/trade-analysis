import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { SignalSource } from '../api/types';
import { InstrumentSelect, useInstruments } from '../components/InstrumentSelect';
import { Pager } from '../components/Pager';
import { SignalCard } from '../components/SignalCard';
import { EmptyState, ErrorMessage, Loading } from '../components/StateViews';
import { useApi } from '../lib/useApi';

const PAGE_SIZE = 20;

export function SignalsPage() {
  const instruments = useInstruments();
  const [symbol, setSymbol] = useState('');
  const [source, setSource] = useState<'' | SignalSource>('');
  const [page, setPage] = useState(0);
  const signals = useApi(
    () => api.signals({ symbol: symbol || undefined, source: source || undefined, page, size: PAGE_SIZE }),
    [symbol, source, page],
  );
  const filtered = symbol !== '' || source !== '';

  return (
    <>
      <h1>Signaux</h1>
      <p className="lead">
        Chaque signal donne une entrée, un stop et un objectif. Touchez un signal pour voir la zone, la liquidation, le
        biais et la session.
      </p>
      <div className="filters card">
        <div className="field">
          <label htmlFor="sig-symbol">Instrument</label>
          <InstrumentSelect
            id="sig-symbol"
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
          <label htmlFor="sig-source">Source</label>
          <select
            id="sig-source"
            value={source}
            onChange={(e) => {
              setSource(e.target.value as '' | SignalSource);
              setPage(0);
            }}
          >
            <option value="">Toutes</option>
            <option value="ENGINE">Moteur du bot</option>
            <option value="TRADINGVIEW">Alertes TradingView</option>
          </select>
        </div>
      </div>
      {signals.loading && <Loading />}
      <ErrorMessage error={signals.error} onRetry={signals.reload} />
      {signals.data && !signals.loading && (
        <>
          <p className="muted small">
            {signals.data.total} {signals.data.total > 1 ? 'signaux' : 'signal'}
            {filtered ? ' avec ces filtres' : ''}, du plus récent au plus ancien.
          </p>
          {signals.data.items.length === 0 ? (
            filtered ? (
              <EmptyState title="Aucun signal avec ces filtres">Choisissez « Tous » ou « Toutes » pour élargir.</EmptyState>
            ) : (
              <EmptyState title="Aucun signal pour l'instant">
                Importez des bougies dans l'écran <Link to="/donnees">Données</Link> : le moteur calcule les signaux
                automatiquement. Les alertes TradingView reçues apparaissent aussi ici.
              </EmptyState>
            )
          ) : (
            <div className="items items-grid">
              {signals.data.items.map((s) => (
                <SignalCard key={s.id} signal={s} />
              ))}
            </div>
          )}
          <Pager page={page} size={PAGE_SIZE} total={signals.data.total} onPage={setPage} />
        </>
      )}
    </>
  );
}
