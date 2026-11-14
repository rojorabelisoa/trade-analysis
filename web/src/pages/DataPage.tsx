import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { IngestResult } from '../api/types';
import { Card } from '../components/Card';
import { InstrumentSelect, useInstruments } from '../components/InstrumentSelect';
import { ErrorMessage } from '../components/StateViews';
import { formatInt, plural } from '../lib/format';
import { useAction } from '../lib/useApi';

function ImportReport({ r }: { r: IngestResult }) {
  return (
    <div className="report" role="status">
      <p>
        <strong>
          Import terminé : {r.symbol}, bougies {r.timeframe}.
        </strong>
      </p>
      <dl className="kv kv-3">
        <div>
          <dt>Reçues</dt>
          <dd>{formatInt(r.received)}</dd>
        </div>
        <div>
          <dt>Nouvelles</dt>
          <dd>{formatInt(r.inserted)}</dd>
        </div>
        <div>
          <dt>Modifiées</dt>
          <dd>{formatInt(r.updated)}</dd>
        </div>
        <div>
          <dt>Déjà présentes</dt>
          <dd>{formatInt(r.unchanged)}</dd>
        </div>
        <div>
          <dt>Écartées (non clôturées)</dt>
          <dd>{formatInt(r.skippedOpen)}</dd>
        </div>
      </dl>
      {r.signals && (
        <>
          <p className="small">Signaux recalculés ensuite :</p>
          <ul className="small">
            {r.signals.timeframes.map((t) => (
              <li key={t.timeframe}>
                {t.skipped
                  ? `${t.timeframe} : non calculé (${t.skipped})`
                  : `${t.timeframe} : ${plural(t.candles, 'bougie', 'bougies')}, ${plural(t.generated, 'signal', 'signaux')} au total dont ${plural(t.recorded, 'nouveau', 'nouveaux')}`}
              </li>
            ))}
          </ul>
          <p className="small">
            Voir les <Link to="/signaux">signaux</Link> et le <Link to="/paper">paper trading</Link>.
          </p>
        </>
      )}
    </div>
  );
}

export function DataPage() {
  const instruments = useInstruments();
  const [symbol, setSymbol] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<IngestResult | null>(null);
  const action = useAction(api.importCandles);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!file || !symbol) return;
    setResult(null);
    const r = await action.run(symbol, file);
    if (r) setResult(r);
  };

  return (
    <>
      <h1>Données</h1>
      <p className="lead">
        Importez un export CSV de TradingView (bougies 5 ou 15 minutes) pour un instrument. Réimporter le même fichier
        ne crée pas de doublon. Après l'import, les signaux et le paper trading sont recalculés automatiquement.
      </p>
      <Card title="Importer des bougies">
        <form className="form" onSubmit={submit}>
          <div className="form-cols">
            <div className="field">
              <label htmlFor="import-symbol">Instrument</label>
              <InstrumentSelect id="import-symbol" value={symbol} onChange={setSymbol} instruments={instruments.data} required />
            </div>
            <div className="field">
              <label htmlFor="import-file">Fichier CSV TradingView</label>
              <input
                id="import-file"
                type="file"
                accept=".csv,text/csv"
                required
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </div>
          </div>
          <p className="small muted">
            Dans TradingView : menu du graphique, « Exporter les données du graphique », heure au format UNIX. Seules les
            bougies clôturées sont gardées.
          </p>
          <div className="btn-row">
            <button type="submit" className="btn btn-primary" disabled={!file || !symbol || action.pending}>
              {action.pending ? 'Import en cours…' : 'Importer'}
            </button>
          </div>
        </form>
        <ErrorMessage error={action.error} title="L'import n'a pas abouti" />
        {result && <ImportReport r={result} />}
      </Card>
    </>
  );
}
