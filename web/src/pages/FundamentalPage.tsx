import { useState, type FormEvent } from 'react';
import { api, ApiError } from '../api/client';
import type { Impact, RecomputeResult } from '../api/types';
import { BiasForm } from '../components/BiasForm';
import { Card } from '../components/Card';
import { ConfirmDelete } from '../components/ConfirmDelete';
import { InstrumentSelect, useInstruments } from '../components/InstrumentSelect';
import { EmptyState, ErrorMessage, Loading } from '../components/StateViews';
import { biasShort, formatDay, impactLabel, todayUtc } from '../lib/format';
import { When } from '../lib/time';
import { useAction, useApi } from '../lib/useApi';

function NewsForm({ onCreated }: { onCreated: () => void }) {
  const [date, setDate] = useState(() => todayUtc());
  const [time, setTime] = useState('13:30');
  const [currency, setCurrency] = useState('USD');
  const [impact, setImpact] = useState<Impact>('high');
  const [event, setEvent] = useState('');
  const create = useAction(api.createNews);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const created = await create.run({
      time: `${date}T${time}:00Z`,
      currency: currency.trim().toUpperCase(),
      impact,
      event: event.trim(),
    });
    if (created) {
      setEvent('');
      onCreated();
    }
  };

  return (
    <form className="form" onSubmit={submit} aria-label="Nouvelle annonce">
      <div className="form-cols">
        <div className="field">
          <label htmlFor="news-date">Date</label>
          <input id="news-date" type="date" value={date} required onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="news-time">Heure (UTC)</label>
          <input id="news-time" type="time" value={time} required onChange={(e) => setTime(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="news-currency">Devise</label>
          <input
            id="news-currency"
            type="text"
            value={currency}
            required
            maxLength={3}
            pattern="[A-Za-z]{3}"
            title="Code à 3 lettres, ex. USD"
            onChange={(e) => setCurrency(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="news-impact">Impact</label>
          <select id="news-impact" value={impact} onChange={(e) => setImpact(e.target.value as Impact)}>
            <option value="high">Fort (bloque les entrées)</option>
            <option value="medium">Moyen</option>
            <option value="low">Faible</option>
          </select>
        </div>
      </div>
      <div className="field">
        <label htmlFor="news-event">Événement</label>
        <input
          id="news-event"
          type="text"
          value={event}
          required
          maxLength={200}
          placeholder="Ex. : Emplois non agricoles (NFP)"
          onChange={(e) => setEvent(e.target.value)}
        />
      </div>
      <div className="btn-row">
        <button type="submit" className="btn btn-primary" disabled={!event.trim() || create.pending}>
          {create.pending ? 'Enregistrement…' : "Enregistrer l'annonce"}
        </button>
      </div>
      <ErrorMessage error={create.error} title="L'annonce n'a pas été enregistrée" />
    </form>
  );
}

function RecomputeCard() {
  const instruments = useInstruments();
  const [symbol, setSymbol] = useState('');
  const [pending, setPending] = useState(false);
  const [results, setResults] = useState<RecomputeResult[]>([]);
  const [error, setError] = useState<ApiError | null>(null);

  const run = async () => {
    const symbols = symbol ? [symbol] : (instruments.data ?? []).map((i) => i.symbol);
    setPending(true);
    setError(null);
    setResults([]);
    const out: RecomputeResult[] = [];
    try {
      for (const s of symbols) {
        out.push(await api.recompute(s));
        setResults([...out]);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)));
    } finally {
      setPending(false);
    }
  };

  return (
    <Card title="Recalculer les signaux">
      <p className="small">
        Modifier un biais ou une annonce <strong>ne recalcule pas</strong> les signaux. Lancez le recalcul quand vous
        avez fini vos changements. Les signaux déjà enregistrés sont conservés ; seuls les nouveaux sont ajoutés.
      </p>
      <form
        className="form-row"
        onSubmit={(e) => {
          e.preventDefault();
          void run();
        }}
      >
        <div className="field">
          <label htmlFor="recompute-symbol">Instrument</label>
          <select id="recompute-symbol" value={symbol} onChange={(e) => setSymbol(e.target.value)}>
            <option value="">Tous les instruments</option>
            {(instruments.data ?? []).map((i) => (
              <option key={i.symbol} value={i.symbol}>
                {i.symbol} — {i.name}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className="btn btn-primary" disabled={pending || !instruments.data}>
          {pending ? 'Recalcul…' : 'Recalculer les signaux'}
        </button>
      </form>
      <ErrorMessage error={error} title="Le recalcul n'a pas abouti" />
      {results.length > 0 && (
        <div className="report" role="status">
          <ul>
            {results.map((r) => (
              <li key={r.symbol}>
                <strong>{r.symbol}</strong> :{' '}
                {r.timeframes.length === 0
                  ? 'aucune bougie stockée'
                  : r.timeframes
                      .map((t) =>
                        t.skipped
                          ? `${t.timeframe} non calculé (${t.skipped})`
                          : `${t.timeframe} : ${t.recorded} ${t.recorded > 1 ? 'nouveaux signaux' : 'nouveau signal'} (${t.generated} au total)`,
                      )
                      .join(' ; ')}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

export function FundamentalPage() {
  const instruments = useInstruments();
  const biases = useApi(() => api.biases(), []);
  const news = useApi(() => api.news(), []);
  const [filter, setFilter] = useState('');
  const delBias = useAction(api.deleteBias);
  const delNews = useAction(api.deleteNews);
  const [now] = useState(() => new Date().toISOString());

  const biasRows = (biases.data ?? [])
    .filter((b) => !filter || b.symbol === filter)
    .slice()
    .sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate) || a.symbol.localeCompare(b.symbol));
  const upcoming = (news.data ?? []).filter((n) => n.time >= now);
  const past = (news.data ?? []).filter((n) => n.time < now).reverse();

  return (
    <>
      <h1>Fondamental</h1>
      <p className="lead">
        Le biais fondamental limite le sens des trades par instrument ; les annonces à fort impact bloquent les entrées
        30 minutes avant et après.
      </p>
      <p className="notice-info" role="note">
        Rappel : modifier un biais ou une annonce ne recalcule pas les signaux. Utilisez le bouton « Recalculer les
        signaux » en bas de la page.
      </p>

      <div className="grid">
        <Card title="Ajouter un biais">
          <BiasForm instruments={instruments.data} onCreated={() => biases.reload()} />
        </Card>

        <Card
          title="Biais saisis"
          action={
            <InstrumentSelect
              id="bias-filter"
              label="Filtrer par instrument"
              value={filter}
              onChange={setFilter}
              instruments={instruments.data}
              allowAll
            />
          }
        >
          {biases.loading && <Loading />}
          <ErrorMessage error={biases.error ?? delBias.error} onRetry={biases.reload} />
          {biases.data && biasRows.length === 0 && (
            <EmptyState title="Aucun biais saisi">
              Sans biais, le bot accepte les deux sens (neutre). Ajoutez un biais avec le formulaire.
            </EmptyState>
          )}
          {biasRows.length > 0 && (
            <ul className="rows">
              {biasRows.map((b) => (
                <li key={b.id} className="row">
                  <div className="row-main">
                    <strong>{b.symbol}</strong> <span className={`badge bias-${b.bias}`}>{biasShort(b.bias)}</span>{' '}
                    <span className="muted small">à partir du {formatDay(b.effectiveDate)}</span>
                    {b.note && <p className="small row-note">{b.note}</p>}
                  </div>
                  <ConfirmDelete
                    label={`le biais ${b.symbol} du ${formatDay(b.effectiveDate)}`}
                    pending={delBias.pending}
                    onConfirm={async () => {
                      await delBias.run(b.id);
                      biases.reload();
                    }}
                  />
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Ajouter une annonce">
          <NewsForm onCreated={() => news.reload()} />
        </Card>

        <Card title="Annonces">
          {news.loading && <Loading />}
          <ErrorMessage error={news.error ?? delNews.error} onRetry={news.reload} />
          {news.data && news.data.length === 0 && (
            <EmptyState title="Aucune annonce saisie">
              Ajoutez les annonces importantes de la semaine (NFP, CPI, Fed…) avec le formulaire.
            </EmptyState>
          )}
          {news.data && news.data.length > 0 && (
            <>
              <h3>À venir</h3>
              {upcoming.length === 0 ? (
                <p className="muted small">Aucune annonce à venir.</p>
              ) : (
                <ul className="rows">
                  {upcoming.map((n) => (
                    <li key={n.id} className="row">
                      <div className="row-main">
                        <When iso={n.time} /> · <strong>{n.currency}</strong> · {n.event}{' '}
                        <span className={`badge impact-${n.impact}`}>{impactLabel(n.impact)}</span>
                      </div>
                      <ConfirmDelete
                        label={`l'annonce ${n.event}`}
                        pending={delNews.pending}
                        onConfirm={async () => {
                          await delNews.run(n.id);
                          news.reload();
                        }}
                      />
                    </li>
                  ))}
                </ul>
              )}
              {past.length > 0 && (
                <details className="past">
                  <summary>Annonces passées ({past.length})</summary>
                  <ul className="rows">
                    {past.map((n) => (
                      <li key={n.id} className="row">
                        <div className="row-main">
                          <When iso={n.time} /> · <strong>{n.currency}</strong> · {n.event}{' '}
                          <span className={`badge impact-${n.impact}`}>{impactLabel(n.impact)}</span>
                        </div>
                        <ConfirmDelete
                          label={`l'annonce ${n.event}`}
                          pending={delNews.pending}
                          onConfirm={async () => {
                            await delNews.run(n.id);
                            news.reload();
                          }}
                        />
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </>
          )}
        </Card>

        <RecomputeCard />
      </div>
    </>
  );
}
