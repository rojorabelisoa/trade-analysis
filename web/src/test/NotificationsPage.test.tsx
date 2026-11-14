import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { Notification, Page } from '../api/types';
import { TimeModeProvider } from '../lib/time';
import { NotificationsPage } from '../pages/NotificationsPage';
import { mockFetch, problem } from './http';

const page = (items: Notification[]): Page<Notification> => ({ items, page: 0, size: 30, total: items.length });

const failed: Notification = {
  id: 7,
  kind: 'SIGNAL',
  subjectId: 3,
  channel: 'TELEGRAM',
  status: 'FAILED',
  reason: 'HTTP 502',
  message: 'GC long',
  attempts: 1,
  createdAt: '2026-01-31T20:50:00Z',
  updatedAt: '2026-01-31T20:50:00Z',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <TimeModeProvider initial="utc">
        <NotificationsPage />
      </TimeModeProvider>
    </MemoryRouter>,
  );
}

describe('écran Notifications', () => {
  it("affiche l'état vide avec ce qu'il faut faire", async () => {
    mockFetch({ 'GET /api/notifications': () => ({ body: page([]) }) });
    renderPage();
    expect(await screen.findByText('Aucune notification')).toBeInTheDocument();
    expect(screen.getByText(/renseignez le jeton du bot/)).toBeInTheDocument();
  });

  it('affiche le message du problem+json en cas d’erreur, puis réessaie', async () => {
    let attempts = 0;
    mockFetch({
      'GET /api/notifications': () => {
        attempts += 1;
        return attempts === 1 ? problem(500, 'Base de données indisponible') : { body: page([]) };
      },
    });
    renderPage();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Base de données indisponible');
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Aucune notification')).toBeInTheDocument();
  });

  it('signale un serveur injoignable', async () => {
    mockFetch({
      'GET /api/notifications': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('Impossible de joindre le serveur');
  });

  it('propose de réessayer une notification en échec', async () => {
    const { calls } = mockFetch({
      'GET /api/notifications': () => ({ body: page([failed]) }),
      'POST /api/notifications/7/retry': () => ({ body: { ...failed, status: 'SENT', reason: null, attempts: 2 } }),
    });
    renderPage();
    expect(await screen.findByText('Échec')).toBeInTheDocument();
    expect(screen.getByText('31/01/2026 20:50 UTC')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(calls.some((c) => c.method === 'POST' && c.url === '/api/notifications/7/retry')).toBe(true);
  });
});
