import { api } from '../api/client';
import type { Instrument } from '../api/types';
import { useApi } from '../lib/useApi';

/** Liste des instruments, chargée une fois par écran. */
export function useInstruments() {
  return useApi<Instrument[]>(() => api.instruments(), []);
}

export function InstrumentSelect({
  id,
  value,
  onChange,
  instruments,
  allowAll = false,
  required = false,
  label,
}: {
  id: string;
  value: string;
  onChange: (v: string) => void;
  instruments: Instrument[] | undefined;
  allowAll?: boolean;
  required?: boolean;
  /** Nom accessible quand le champ n'a pas d'étiquette visible. */
  label?: string;
}) {
  return (
    <select id={id} aria-label={label} value={value} onChange={(e) => onChange(e.target.value)} required={required}>
      {allowAll && <option value="">Tous</option>}
      {!allowAll && <option value="">Choisir…</option>}
      {(instruments ?? []).map((i) => (
        <option key={i.symbol} value={i.symbol}>
          {i.symbol} — {i.name}
        </option>
      ))}
    </select>
  );
}
