import type { ReactNode } from 'react';
import type { ApiError } from '../api/client';

export function Loading({ label = 'Chargement…' }: { label?: string }) {
  return (
    <div className="state state-loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      {label}
    </div>
  );
}

/** État vide : ce qu'il n'y a pas, et ce qu'il faut faire pour le remplir. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state state-empty">
      <p className="state-title">{title}</p>
      {children && <div className="state-hint">{children}</div>}
    </div>
  );
}

/** Erreur : message du backend (problem+json) et détails de validation éventuels. */
export function ErrorMessage({
  error,
  onRetry,
  title = 'Erreur',
}: {
  error: ApiError | null;
  onRetry?: () => void;
  title?: string;
}) {
  if (!error) return null;
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">{title}</p>
      <p>{error.message}</p>
      {error.details.length > 0 && (
        <ul className="error-details">
          {error.details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </ul>
      )}
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Réessayer
        </button>
      )}
    </div>
  );
}
