import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { BiasEntry, BiasValue, Instrument } from '../api/types';
import { biasLabel, todayUtc } from '../lib/format';
import { useAction } from '../lib/useApi';
import { InstrumentSelect } from './InstrumentSelect';
import { ErrorMessage } from './StateViews';

const BIAS_VALUES: BiasValue[] = ['long', 'short', 'neutral', 'off'];

/** Saisie d'un biais fondamental : date d'effet (minuit UTC), instrument, sens, note. */
export function BiasForm({
  instruments,
  onCreated,
}: {
  instruments: Instrument[] | undefined;
  onCreated: (b: BiasEntry) => void;
}) {
  const [date, setDate] = useState(() => todayUtc());
  const [symbol, setSymbol] = useState('');
  const [bias, setBias] = useState<BiasValue>('neutral');
  const [note, setNote] = useState('');
  const create = useAction(api.createBias);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!date || !symbol) return;
    const created = await create.run({ date, symbol, bias, ...(note.trim() ? { note: note.trim() } : {}) });
    if (created) {
      setNote('');
      onCreated(created);
    }
  };

  return (
    <form className="form" onSubmit={submit} aria-label="Nouveau biais">
      <div className="form-cols">
        <div className="field">
          <label htmlFor="bias-date">Date d'effet</label>
          <input id="bias-date" type="date" value={date} required onChange={(e) => setDate(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="bias-symbol">Instrument</label>
          <InstrumentSelect id="bias-symbol" value={symbol} onChange={setSymbol} instruments={instruments} required />
        </div>
      </div>
      <fieldset className="field radio-group">
        <legend>Sens</legend>
        {BIAS_VALUES.map((b) => (
          <label key={b} className="radio">
            <input type="radio" name="bias" value={b} checked={bias === b} onChange={() => setBias(b)} />
            {biasLabel(b)}
          </label>
        ))}
      </fieldset>
      <div className="field">
        <label htmlFor="bias-note">Note (facultatif)</label>
        <input
          id="bias-note"
          type="text"
          maxLength={500}
          value={note}
          placeholder="Ex. : Fed accommodante, dollar faible"
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      <p className="small muted">
        Le biais s'applique à partir de minuit UTC à la date choisie, jusqu'au biais suivant du même instrument.
      </p>
      <div className="btn-row">
        <button type="submit" className="btn btn-primary" disabled={!date || !symbol || create.pending}>
          {create.pending ? 'Enregistrement…' : 'Enregistrer le biais'}
        </button>
      </div>
      <ErrorMessage error={create.error} title="Le biais n'a pas été enregistré" />
    </form>
  );
}
