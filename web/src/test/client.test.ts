import { describe, expect, it } from 'vitest';
import { api, ApiError } from '../api/client';
import { strategyOverrides } from '../pages/BacktestPage';
import { mockFetch, problem } from './http';

describe('client API', () => {
  it('construit les chemins relatifs avec les filtres renseignés seulement', async () => {
    const { calls } = mockFetch({ 'GET /api/signals': () => ({ body: { items: [], page: 0, size: 5, total: 0 } }) });
    await api.signals({ symbol: 'GC', source: undefined, size: 5 });
    expect(calls[0]!.url).toBe('/api/signals?symbol=GC&size=5');
  });

  it('transforme un problem+json en ApiError lisible', async () => {
    mockFetch({ 'GET /api/signals': () => problem(400, "Instrument inconnu : 'XX'") });
    const err = await api.signals({ symbol: 'XX' }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(400);
    expect((err as ApiError).message).toBe("Instrument inconnu : 'XX'");
  });

  it('accepte une réponse 204 sans corps', async () => {
    mockFetch({ 'DELETE /api/bias/3': () => ({ status: 204 }) });
    await expect(api.deleteBias(3)).resolves.toBeUndefined();
  });

  it("n'envoie que les réglages de stratégie modifiés", () => {
    expect(strategyOverrides({ trendMode: '', targetMode: '', rr: '', stopMode: '', minTouches: '', minRr: '' })).toEqual({});
    expect(
      strategyOverrides({ trendMode: 'off', targetMode: 'rr', rr: '2,5', stopMode: 'h4', minTouches: '3', minRr: '1.5' }),
    ).toEqual({ trendMode: 'off', targetMode: 'rr', rr: 2.5, stopMode: 'h4', minTouches: 3, minRr: 1.5 });
  });
});
