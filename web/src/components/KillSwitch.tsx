import { useState } from 'react';
import { api } from '../api/client';
import type { TradingStatus } from '../api/types';
import { useAction } from '../lib/useApi';
import { When } from '../lib/time';
import { ErrorMessage } from './StateViews';

type Step = 'idle' | 'confirm-halt' | 'confirm-resume';

/**
 * Coupe-circuit : état, arrêt (motif obligatoire) et reprise, chacun avec une étape de
 * confirmation dans la page.
 */
export function KillSwitch({ status, onChange }: { status: TradingStatus; onChange: (s: TradingStatus) => void }) {
  const [step, setStep] = useState<Step>('idle');
  const [reason, setReason] = useState('');
  const halt = useAction(api.halt);
  const resume = useAction(api.resume);
  const pending = halt.pending || resume.pending;
  const error = halt.error ?? resume.error;

  const cancel = () => {
    setStep('idle');
    setReason('');
    halt.clearError();
    resume.clearError();
  };

  const confirmHalt = async () => {
    const s = await halt.run(reason.trim());
    if (s) {
      onChange(s);
      setStep('idle');
      setReason('');
    }
  };

  const confirmResume = async () => {
    const s = await resume.run();
    if (s) {
      onChange(s);
      setStep('idle');
    }
  };

  return (
    <div className={`killswitch ${status.halted ? 'is-halted' : 'is-running'}`}>
      <p className="ks-state" aria-live="polite">
        <span className="ks-dot" aria-hidden="true" />
        {status.halted ? (
          <strong>Trading arrêté</strong>
        ) : (
          <strong>Trading actif</strong>
        )}
      </p>
      {status.halted ? (
        <p className="small">
          Arrêté depuis le <When iso={status.since} />. Motif : « {status.reason} ».
          <br />
          Aucun nouveau trade simulé n'est pris tant que le coupe-circuit est actif.
        </p>
      ) : (
        <p className="small muted">
          Le coupe-circuit arrête immédiatement la prise de nouveaux trades simulés (et, plus tard, de tout ordre
          réel).
        </p>
      )}

      {step === 'idle' && !status.halted && (
        <button type="button" className="btn btn-danger" onClick={() => setStep('confirm-halt')}>
          Arrêter le trading
        </button>
      )}
      {step === 'idle' && status.halted && (
        <button type="button" className="btn btn-primary" onClick={() => setStep('confirm-resume')}>
          Reprendre le trading
        </button>
      )}

      {step === 'confirm-halt' && (
        <form
          className="confirm-box"
          onSubmit={(e) => {
            e.preventDefault();
            if (reason.trim()) void confirmHalt();
          }}
        >
          <label htmlFor="halt-reason">Motif de l'arrêt (obligatoire)</label>
          <textarea
            id="halt-reason"
            value={reason}
            maxLength={500}
            rows={2}
            placeholder="Ex. : annonce de la Fed, comportement anormal…"
            onChange={(e) => setReason(e.target.value)}
            autoFocus
          />
          <p className="small">Confirmez-vous l'arrêt du trading ?</p>
          <div className="btn-row">
            <button type="submit" className="btn btn-danger" disabled={!reason.trim() || pending}>
              {pending ? 'Arrêt…' : "Confirmer l'arrêt"}
            </button>
            <button type="button" className="btn btn-secondary" onClick={cancel} disabled={pending}>
              Annuler
            </button>
          </div>
        </form>
      )}

      {step === 'confirm-resume' && (
        <div className="confirm-box">
          <p className="small">
            Confirmez-vous la reprise ? Les nouveaux signaux pourront de nouveau ouvrir des trades simulés.
          </p>
          <div className="btn-row">
            <button type="button" className="btn btn-primary" onClick={() => void confirmResume()} disabled={pending}>
              {pending ? 'Reprise…' : 'Confirmer la reprise'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={cancel} disabled={pending}>
              Annuler
            </button>
          </div>
        </div>
      )}
      <ErrorMessage error={error} title="L'action n'a pas abouti" />
    </div>
  );
}
