import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { browserTimeZone, formatDateTime, formatTime, type TimeMode } from './format';

const STORAGE_KEY = 'srliq.timeMode';

interface TimeCtx {
  mode: TimeMode;
  setMode: (m: TimeMode) => void;
  zone: string;
}

const Ctx = createContext<TimeCtx>({ mode: 'local', setMode: () => {}, zone: 'local' });

function readStored(): TimeMode {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'utc' ? 'utc' : 'local';
  } catch {
    return 'local';
  }
}

export function TimeModeProvider({ children, initial }: { children: ReactNode; initial?: TimeMode }) {
  const [mode, setModeState] = useState<TimeMode>(() => initial ?? readStored());
  const setMode = useCallback((m: TimeMode) => {
    setModeState(m);
    try {
      localStorage.setItem(STORAGE_KEY, m);
    } catch {
      /* stockage indisponible : préférence non mémorisée */
    }
  }, []);
  const value = useMemo(() => ({ mode, setMode, zone: browserTimeZone() }), [mode, setMode]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useTimeMode(): TimeCtx {
  return useContext(Ctx);
}

/**
 * Heure affichée dans le fuseau choisi ; l'autre (UTC ou heure locale) est donnée au survol
 * et lue par les lecteurs d'écran via l'attribut title.
 */
export function When({ iso, timeOnly = false }: { iso: string | null | undefined; timeOnly?: boolean }) {
  const { mode } = useTimeMode();
  if (!iso) return <span>—</span>;
  const fmt = timeOnly ? formatTime : formatDateTime;
  const other = mode === 'utc' ? `${fmt(iso, 'local')} (heure locale)` : fmt(iso, 'utc');
  return (
    <time dateTime={iso} title={other}>
      {fmt(iso, mode)}
    </time>
  );
}
