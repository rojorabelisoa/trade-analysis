import type {
  BacktestResult,
  BiasEntry,
  CreateBias,
  CreateNews,
  IngestResult,
  Instrument,
  NewsEvent,
  Notification,
  NotificationKind,
  NotificationStatus,
  Page,
  PaperSummary,
  PaperTrade,
  PaperTradeStatus,
  ProblemDetail,
  RecomputeResult,
  ReplayResult,
  SignalRecord,
  SignalSource,
  StrategyOverrides,
  TradingStatus,
} from './types';

/** Erreur d'appel à l'API, avec le message lisible tiré du problem+json du backend. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, message: string, problem: ProblemDetail | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  /** Détails de validation (champ « errors » du backend), s'il y en a. */
  get details(): string[] {
    return this.problem?.errors ?? [];
  }
}

export const NETWORK_ERROR_MESSAGE =
  "Impossible de joindre le serveur. Vérifiez que l'application (backend) est démarrée.";

function messageFor(status: number, problem: ProblemDetail | null): string {
  if (problem?.detail) return problem.detail;
  if (status === 404) return 'Ressource introuvable.';
  if (status >= 500) return 'Erreur du serveur. Réessayez dans un instant.';
  return problem?.title ?? `Erreur ${status}`;
}

type Query = Record<string, string | number | boolean | null | undefined>;

function withQuery(path: string, query?: Query): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

/** Appel à l'API : chemins relatifs (/api/...), JSON en retour, erreurs converties en ApiError. */
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: { Accept: 'application/json, application/problem+json', ...(init.headers ?? {}) },
    });
  } catch {
    throw new ApiError(0, NETWORK_ERROR_MESSAGE);
  }
  if (!res.ok) {
    let problem: ProblemDetail | null = null;
    try {
      const text = await res.text();
      problem = text ? (JSON.parse(text) as ProblemDetail) : null;
    } catch {
      problem = null;
    }
    // Le proxy Vite renvoie 502/504 quand le backend est arrêté.
    if ((res.status === 502 || res.status === 504) && !problem?.detail) {
      throw new ApiError(res.status, NETWORK_ERROR_MESSAGE);
    }
    throw new ApiError(res.status, messageFor(res.status, problem), problem);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

function postJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export const api = {
  instruments: () => request<Instrument[]>('/api/instruments'),

  signals: (q: { symbol?: string; source?: SignalSource; page?: number; size?: number } = {}) =>
    request<Page<SignalRecord>>(withQuery('/api/signals', q)),
  recompute: (symbol: string) =>
    postJson<RecomputeResult>(withQuery('/api/signals/recompute', { symbol })),

  tradingStatus: () => request<TradingStatus>('/api/trading/status'),
  halt: (reason: string) => postJson<TradingStatus>('/api/trading/halt', { reason }),
  resume: () => postJson<TradingStatus>('/api/trading/resume'),

  paperSummary: (q: { symbol?: string } = {}) =>
    request<PaperSummary>(withQuery('/api/paper/summary', q)),
  paperTrades: (q: { symbol?: string; status?: PaperTradeStatus; page?: number; size?: number } = {}) =>
    request<Page<PaperTrade>>(withQuery('/api/paper/trades', q)),
  replay: (symbol: string) => postJson<ReplayResult>(withQuery('/api/paper/replay', { symbol })),

  biases: () => request<BiasEntry[]>('/api/bias'),
  createBias: (body: CreateBias) => postJson<BiasEntry>('/api/bias', body),
  deleteBias: (id: number) => request<void>(`/api/bias/${id}`, { method: 'DELETE' }),

  news: (q: { from?: string; to?: string } = {}) => request<NewsEvent[]>(withQuery('/api/news', q)),
  createNews: (body: CreateNews) => postJson<NewsEvent>('/api/news', body),
  deleteNews: (id: number) => request<void>(`/api/news/${id}`, { method: 'DELETE' }),

  notifications: (
    q: { kind?: NotificationKind; status?: NotificationStatus; page?: number; size?: number } = {},
  ) => request<Page<Notification>>(withQuery('/api/notifications', q)),
  retryNotification: (id: number) => postJson<Notification>(`/api/notifications/${id}/retry`),

  importCandles: (symbol: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    form.append('symbol', symbol);
    return request<IngestResult>('/api/candles/import', { method: 'POST', body: form });
  },

  backtest: (p: {
    file: File;
    symbol: string;
    cost?: number;
    intraday: boolean;
    useBias: boolean;
    useNews: boolean;
    strategy: StrategyOverrides;
  }) => {
    const form = new FormData();
    form.append('file', p.file);
    form.append('symbol', p.symbol);
    if (p.cost !== undefined) form.append('cost', String(p.cost));
    form.append('intraday', String(p.intraday));
    form.append('useBias', String(p.useBias));
    form.append('useNews', String(p.useNews));
    if (Object.keys(p.strategy).length > 0) form.append('strategy', JSON.stringify(p.strategy));
    return request<BacktestResult>('/api/backtests', { method: 'POST', body: form });
  },
};
