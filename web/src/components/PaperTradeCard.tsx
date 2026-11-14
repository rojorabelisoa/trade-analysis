import type { PaperTrade } from '../api/types';
import { formatCurrency, formatPrice, formatR, outcomeLabel, sessionLabel } from '../lib/format';
import { When } from '../lib/time';
import { DirectionBadge } from './SignalCard';
import { rClass } from './Stats';

/** Trade simulé : sens, statut, prix, résultat en R et en devise, taille de position. */
export function PaperTradeCard({ trade }: { trade: PaperTrade }) {
  const t = trade;
  const open = t.status === 'OPEN';
  return (
    <article className="item">
      <div className="item-head static">
        <span className="item-title">
          <strong>{t.symbol}</strong> <DirectionBadge direction={t.direction} />
          <span className={`badge ${open ? 'badge-open' : 'badge-neutral'}`}>{open ? 'Ouvert' : 'Fermé'}</span>
        </span>
        <span className="item-sub">
          Entrée <When iso={t.entryTime} /> · {sessionLabel(t.session)} · {t.timeframe}
        </span>
      </div>
      <dl className="kv kv-3">
        <div>
          <dt>Entrée</dt>
          <dd>{formatPrice(t.entry)}</dd>
        </div>
        <div>
          <dt>Stop</dt>
          <dd>{formatPrice(t.stop)}</dd>
        </div>
        <div>
          <dt>Objectif</dt>
          <dd>{formatPrice(t.target)}</dd>
        </div>
        <div>
          <dt>{open ? 'Dernier prix' : 'Sortie'}</dt>
          <dd>{formatPrice(open ? t.lastPrice : t.exitPrice)}</dd>
        </div>
        <div>
          <dt>{open ? 'Résultat latent' : 'Résultat'}</dt>
          <dd className={rClass(t.r)}>{formatR(t.r)}</dd>
        </div>
        <div>
          <dt>En devise</dt>
          <dd className={rClass(t.pnlCurrency)}>{formatCurrency(t.pnlCurrency)}</dd>
        </div>
        <div>
          <dt>Contrats</dt>
          <dd>{t.quantity}</dd>
        </div>
        <div>
          <dt>{open ? 'Dernière bougie' : 'Type de sortie'}</dt>
          <dd>{open ? <When iso={t.lastTime} /> : outcomeLabel(t.outcome)}</dd>
        </div>
        {!open && (
          <div>
            <dt>Heure de sortie</dt>
            <dd>
              <When iso={t.exitTime} />
            </dd>
          </div>
        )}
      </dl>
      {t.riskTooLarge && (
        <p className="warn-line" role="note">
          ⚠ Risque trop grand pour le capital : un seul contrat dépasse le risque permis par trade, aucune position
          n'est prise (0 contrat).
        </p>
      )}
    </article>
  );
}
