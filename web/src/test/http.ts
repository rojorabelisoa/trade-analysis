import { vi } from 'vitest';

export interface Call {
  method: string;
  url: string;
  body: unknown;
}

type Handler = (call: Call) => { status?: number; body?: unknown; problem?: boolean };

/** Remplace fetch : chaque appel est routé vers le premier gestionnaire dont la clé « MÉTHODE chemin » correspond. */
export function mockFetch(routes: Record<string, Handler>) {
  const calls: Call[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init?.method ?? 'GET').toUpperCase();
    let body: unknown = init?.body;
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body);
      } catch {
        /* texte brut */
      }
    }
    const call = { method, url, body };
    calls.push(call);
    const path = url.split('?')[0];
    const key = Object.keys(routes).find((k) => k === `${method} ${path}` || k === `${method} ${url}`);
    if (!key) throw new Error(`Appel inattendu : ${method} ${url}`);
    const r = routes[key]!(call);
    const status = r.status ?? 200;
    const text = r.body === undefined ? '' : JSON.stringify(r.body);
    return new Response(status === 204 ? null : text, {
      status,
      headers: { 'Content-Type': r.problem || status >= 400 ? 'application/problem+json' : 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fn);
  return { fn, calls };
}

export const problem = (status: number, detail: string, errors?: string[]) => ({
  status,
  problem: true,
  body: { type: 'about:blank', title: 'Erreur', status, detail, ...(errors ? { errors } : {}) },
});
