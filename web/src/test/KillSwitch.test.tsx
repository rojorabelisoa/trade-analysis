import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { TradingStatus } from '../api/types';
import { KillSwitch } from '../components/KillSwitch';
import { TimeModeProvider } from '../lib/time';
import { mockFetch, problem } from './http';

const running: TradingStatus = {
  halted: false,
  since: null,
  reason: null,
  paperTrading: true,
  liveTradingEnabled: false,
  broker: 'paper',
  history: [],
};
const halted: TradingStatus = {
  ...running,
  halted: true,
  since: '2026-01-31T20:50:00Z',
  reason: 'Annonce Fed',
  history: [{ id: 1, startedAt: '2026-01-31T20:50:00Z', endedAt: null, reason: 'Annonce Fed' }],
};

function renderKs(status: TradingStatus, onChange = vi.fn()) {
  render(
    <TimeModeProvider initial="utc">
      <KillSwitch status={status} onChange={onChange} />
    </TimeModeProvider>,
  );
  return onChange;
}

describe('coupe-circuit', () => {
  it("demande un motif et une confirmation dans la page avant d'arrêter", async () => {
    const confirmSpy = vi.spyOn(window, 'confirm');
    const { calls } = mockFetch({ 'POST /api/trading/halt': () => ({ body: halted }) });
    const onChange = renderKs(running);

    expect(screen.getByText('Trading actif')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Arrêter le trading' }));

    const confirm = screen.getByRole('button', { name: "Confirmer l'arrêt" });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/Motif de l'arrêt/), '   ');
    expect(confirm).toBeDisabled();
    await userEvent.clear(screen.getByLabelText(/Motif de l'arrêt/));
    await userEvent.type(screen.getByLabelText(/Motif de l'arrêt/), 'Annonce Fed');
    expect(calls).toHaveLength(0);

    await userEvent.click(confirm);
    expect(calls).toEqual([{ method: 'POST', url: '/api/trading/halt', body: { reason: 'Annonce Fed' } }]);
    expect(onChange).toHaveBeenCalledWith(halted);
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it("annuler n'envoie rien", async () => {
    const { calls } = mockFetch({});
    const onChange = renderKs(running);
    await userEvent.click(screen.getByRole('button', { name: 'Arrêter le trading' }));
    await userEvent.type(screen.getByLabelText(/Motif de l'arrêt/), 'test');
    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(screen.queryByLabelText(/Motif de l'arrêt/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Arrêter le trading' })).toBeInTheDocument();
    expect(calls).toHaveLength(0);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('demande confirmation avant de reprendre', async () => {
    const { calls } = mockFetch({ 'POST /api/trading/resume': () => ({ body: running }) });
    const onChange = renderKs(halted);
    expect(screen.getByText('Trading arrêté')).toBeInTheDocument();
    expect(screen.getByText(/Annonce Fed/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reprendre le trading' }));
    expect(calls).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Confirmer la reprise' }));
    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual(['POST /api/trading/resume']);
    expect(onChange).toHaveBeenCalledWith(running);
  });

  it("affiche l'erreur du serveur et garde la saisie", async () => {
    mockFetch({ 'POST /api/trading/halt': () => problem(400, 'Entrée invalide', ['reason : must not be blank']) });
    const onChange = renderKs(running);
    await userEvent.click(screen.getByRole('button', { name: 'Arrêter le trading' }));
    await userEvent.type(screen.getByLabelText(/Motif de l'arrêt/), 'x');
    await userEvent.click(screen.getByRole('button', { name: "Confirmer l'arrêt" }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Entrée invalide');
    expect(alert).toHaveTextContent('reason : must not be blank');
    expect(screen.getByLabelText(/Motif de l'arrêt/)).toHaveValue('x');
    expect(onChange).not.toHaveBeenCalled();
  });
});
