import { useState } from 'react';
import { api } from '../api/client';
import type { Notification, NotificationKind, NotificationStatus } from '../api/types';
import { Pager } from '../components/Pager';
import { EmptyState, ErrorMessage, Loading } from '../components/StateViews';
import { notificationKindLabel, notificationStatusLabel } from '../lib/format';
import { When } from '../lib/time';
import { useAction, useApi } from '../lib/useApi';

const PAGE_SIZE = 30;
const KINDS: NotificationKind[] = ['SIGNAL', 'TRADE_CLOSED', 'HALT', 'RESUME'];
const STATUSES: NotificationStatus[] = ['SENT', 'FAILED', 'SKIPPED', 'PENDING'];

function NotificationItem({ n, onRetried }: { n: Notification; onRetried: (n: Notification) => void }) {
  const retry = useAction(api.retryNotification);
  return (
    <article className="item">
      <div className="item-head static">
        <span className="item-title">
          <strong>{notificationKindLabel(n.kind)}</strong>
          <span className={`badge status-${n.status.toLowerCase()}`}>{notificationStatusLabel(n.status)}</span>
        </span>
        <span className="item-sub">
          <When iso={n.createdAt} /> · {n.channel === 'TELEGRAM' ? 'Telegram' : n.channel}
          {n.attempts > 0 && ` · ${n.attempts} tentative${n.attempts > 1 ? 's' : ''}`}
        </span>
      </div>
      {n.reason && (
        <p className="small">
          Motif : <span className="muted">{n.reason}</span>
        </p>
      )}
      {n.message && (
        <details>
          <summary className="small">Voir le message</summary>
          <pre className="message">{n.message}</pre>
        </details>
      )}
      {n.status === 'FAILED' && (
        <div className="btn-row">
          <button
            type="button"
            className="btn btn-secondary btn-small"
            disabled={retry.pending}
            onClick={async () => {
              const r = await retry.run(n.id);
              if (r) onRetried(r);
            }}
          >
            {retry.pending ? 'Renvoi…' : 'Réessayer'}
          </button>
        </div>
      )}
      <ErrorMessage error={retry.error} title="Le renvoi a échoué" />
    </article>
  );
}

export function NotificationsPage() {
  const [kind, setKind] = useState<'' | NotificationKind>('');
  const [status, setStatus] = useState<'' | NotificationStatus>('');
  const [page, setPage] = useState(0);
  const list = useApi(
    () => api.notifications({ kind: kind || undefined, status: status || undefined, page, size: PAGE_SIZE }),
    [kind, status, page],
  );
  const filtered = kind !== '' || status !== '';

  return (
    <>
      <h1>Notifications</h1>
      <p className="lead">
        Messages Telegram : signaux récents, trades simulés terminés, coupe-circuit. « Non envoyée » est normal quand
        Telegram n'est pas configuré ou quand le signal est trop ancien.
      </p>
      <div className="filters card">
        <div className="field">
          <label htmlFor="notif-kind">Type</label>
          <select
            id="notif-kind"
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as '' | NotificationKind);
              setPage(0);
            }}
          >
            <option value="">Tous</option>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {notificationKindLabel(k)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="notif-status">Statut</label>
          <select
            id="notif-status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as '' | NotificationStatus);
              setPage(0);
            }}
          >
            <option value="">Tous</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {notificationStatusLabel(s)}
              </option>
            ))}
          </select>
        </div>
      </div>
      {list.loading && <Loading />}
      <ErrorMessage error={list.error} onRetry={list.reload} />
      {list.data && !list.loading && (
        <>
          {list.data.items.length === 0 ? (
            filtered ? (
              <EmptyState title="Aucune notification avec ces filtres">Choisissez « Tous » pour élargir.</EmptyState>
            ) : (
              <EmptyState title="Aucune notification">
                Les notifications apparaissent ici dès qu'un signal est détecté, qu'un trade simulé se termine ou que le
                coupe-circuit change d'état. Pour les recevoir sur Telegram, renseignez le jeton du bot et la discussion
                dans la configuration du serveur (voir le README).
              </EmptyState>
            )
          ) : (
            <>
              <p className="muted small">
                {list.data.total} notification{list.data.total > 1 ? 's' : ''}, de la plus récente à la plus ancienne.
              </p>
              <div className="items items-grid">
                {list.data.items.map((n) => (
                  <NotificationItem key={n.id} n={n} onRetried={() => list.reload()} />
                ))}
              </div>
            </>
          )}
          <Pager page={page} size={PAGE_SIZE} total={list.data.total} onPage={setPage} />
        </>
      )}
    </>
  );
}
