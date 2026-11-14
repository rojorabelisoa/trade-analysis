import { useState } from 'react';

/** Suppression en deux temps, dans la page (pas de fenêtre du navigateur). */
export function ConfirmDelete({ label, onConfirm, pending }: { label: string; onConfirm: () => void; pending?: boolean }) {
  const [asking, setAsking] = useState(false);
  if (!asking) {
    return (
      <button type="button" className="btn btn-small btn-secondary" onClick={() => setAsking(true)} aria-label={`Supprimer ${label}`}>
        Supprimer
      </button>
    );
  }
  return (
    <span className="confirm-inline">
      <button type="button" className="btn btn-small btn-danger" onClick={onConfirm} disabled={pending}>
        Confirmer
      </button>
      <button type="button" className="btn btn-small btn-secondary" onClick={() => setAsking(false)} disabled={pending}>
        Annuler
      </button>
    </span>
  );
}
