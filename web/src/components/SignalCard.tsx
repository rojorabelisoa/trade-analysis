import { useState } from 'react';
import type { SignalRecord } from '../api/types';
import {
  biasLabel,
  directionLabel,
  formatInt,
  formatPrice,
  formatRr,
  sessionLabel,
  sourceLabel,
} from '../lib/format';
import { When } from '../lib/time';

export function DirectionBadge({ direction }: { direction: string }) {
  return (
    <span className={`badge ${direction === 'long' ? 'badge-long' : 'badge-short'}`}>
      {direction === 'long' ? '▲' : '▼'} {directionLabel(direction)}
    </span>
  );
}

/** Signal : résumé (instrument, sens, heure, entrée/stop/objectif) et détail au toucher. */
export function SignalCard({ signal, initiallyOpen = false }: { signal: SignalRecord; initiallyOpen?: boolean }) {
  const [open, setOpen] = useState(initiallyOpen);
  const s = signal;
  return (
    <article className="item">
      <button
        type="button"
        className="item-head"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <span className="item-title">
          <strong>{s.symbol}</strong> <DirectionBadge direction={s.direction} />
        </span>
        <span className="item-sub">
          <When iso={s.time} /> · {sourceLabel(s.source)} · {s.timeframe}
        </span>
      </button>
      <dl className="kv kv-3">
        <div>
          <dt>Entrée</dt>
          <dd>{formatPrice(s.entry)}</dd>
        </div>
        <div>
          <dt>Stop</dt>
          <dd>{formatPrice(s.stop)}</dd>
        </div>
        <div>
          <dt>Objectif</dt>
          <dd>{formatPrice(s.target)}</dd>
        </div>
      </dl>
      {open && (
        <dl className="kv kv-detail">
          <div>
            <dt>Rapport gain / risque (R:R)</dt>
            <dd>{formatRr(s.rr)}</dd>
          </div>
          <div>
            <dt>Zone</dt>
            <dd>{s.zone ?? '—'}</dd>
          </div>
          {s.contacts !== null && (
            <div>
              <dt>Contacts de la zone</dt>
              <dd>{formatInt(s.contacts)}</dd>
            </div>
          )}
          <div>
            <dt>Liquidation (mèche)</dt>
            <dd>
              {s.sweepExtreme !== null ? formatPrice(s.sweepExtreme) : '—'}
              {s.sweepTime && (
                <>
                  {' '}
                  (<When iso={s.sweepTime} />)
                </>
              )}
            </dd>
          </div>
          {s.nextZone !== null && (
            <div>
              <dt>Zone suivante</dt>
              <dd>{formatPrice(s.nextZone)}</dd>
            </div>
          )}
          <div>
            <dt>Biais</dt>
            <dd>{biasLabel(s.bias)}</dd>
          </div>
          <div>
            <dt>Session</dt>
            <dd>{sessionLabel(s.session)}</dd>
          </div>
          <div>
            <dt>Reçu le</dt>
            <dd>
              <When iso={s.receivedAt} />
            </dd>
          </div>
        </dl>
      )}
      <button type="button" className="link-btn" onClick={() => setOpen((o) => !o)}>
        {open ? 'Masquer le détail' : 'Voir le détail'}
      </button>
    </article>
  );
}
