import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';

export interface ApiState<T> {
  data: T | undefined;
  error: ApiError | null;
  loading: boolean;
  reload: () => void;
}

function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(0, e instanceof Error ? e.message : String(e));
}

/**
 * Charge une ressource et suit son état (chargement, erreur, données). {@code deps} relance
 * le chargement ; une réponse arrivée après un changement de paramètres est ignorée.
 */
export function useApi<T>(load: () => Promise<T>, deps: unknown[]): ApiState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    loadRef
      .current()
      .then((d) => {
        if (active) setData(d);
      })
      .catch((e: unknown) => {
        if (active) setError(toApiError(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

/** Action ponctuelle (envoi d'un formulaire, bouton) : état d'envoi et dernière erreur. */
export function useAction<A extends unknown[], R>(fn: (...args: A) => Promise<R>) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const run = useCallback(
    async (...args: A): Promise<R | undefined> => {
      setPending(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (e) {
        setError(toApiError(e));
        return undefined;
      } finally {
        setPending(false);
      }
    },
    [fn],
  );
  return { run, pending, error, clearError: () => setError(null) };
}
