import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Instrument } from '../api/types';
import { BiasForm } from '../components/BiasForm';
import { todayUtc } from '../lib/format';
import { mockFetch, problem } from './http';

const instruments: Instrument[] = [
  { id: 1, symbol: 'GC', name: 'Or (COMEX Gold)', pointValue: 100 },
  { id: 2, symbol: 'MGC', name: 'Micro Or', pointValue: 10 },
];

describe('formulaire de biais', () => {
  it('envoie la date, l’instrument, le sens et la note', async () => {
    const created = { id: 9, effectiveDate: '2026-02-02', symbol: 'GC', bias: 'short', note: 'Fed restrictive' };
    const { calls } = mockFetch({ 'POST /api/bias': () => ({ status: 201, body: created }) });
    const onCreated = vi.fn();
    render(<BiasForm instruments={instruments} onCreated={onCreated} />);

    expect(screen.getByLabelText("Date d'effet")).toHaveValue(todayUtc());
    const submit = screen.getByRole('button', { name: 'Enregistrer le biais' });
    expect(submit).toBeDisabled();

    await userEvent.clear(screen.getByLabelText("Date d'effet"));
    await userEvent.type(screen.getByLabelText("Date d'effet"), '2026-02-02');
    await userEvent.selectOptions(screen.getByLabelText('Instrument'), 'GC');
    await userEvent.click(screen.getByLabelText(/Short/));
    await userEvent.type(screen.getByLabelText(/Note/), '  Fed restrictive ');
    await userEvent.click(submit);

    expect(calls).toEqual([
      {
        method: 'POST',
        url: '/api/bias',
        body: { date: '2026-02-02', symbol: 'GC', bias: 'short', note: 'Fed restrictive' },
      },
    ]);
    expect(onCreated).toHaveBeenCalledWith(created);
    expect(screen.getByLabelText(/Note/)).toHaveValue('');
  });

  it('n’envoie pas de note vide et propose les quatre sens', async () => {
    const { calls } = mockFetch({
      'POST /api/bias': () => ({ status: 201, body: { id: 1, effectiveDate: todayUtc(), symbol: 'MGC', bias: 'neutral', note: null } }),
    });
    render(<BiasForm instruments={instruments} onCreated={vi.fn()} />);
    expect(screen.getAllByRole('radio')).toHaveLength(4);
    expect(screen.getByLabelText(/Neutre/)).toBeChecked();
    await userEvent.selectOptions(screen.getByLabelText('Instrument'), 'MGC');
    await userEvent.click(screen.getByLabelText(/Off/));
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le biais' }));
    expect(calls[0]!.body).toEqual({ date: todayUtc(), symbol: 'MGC', bias: 'off' });
  });

  it('affiche le refus du serveur (biais déjà saisi pour cette date)', async () => {
    mockFetch({ 'POST /api/bias': () => problem(409, 'Cette entrée existe déjà') });
    const onCreated = vi.fn();
    render(<BiasForm instruments={instruments} onCreated={onCreated} />);
    await userEvent.selectOptions(screen.getByLabelText('Instrument'), 'GC');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer le biais' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Cette entrée existe déjà');
    expect(onCreated).not.toHaveBeenCalled();
  });
});
