import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EquityChart, niceScale } from '../components/EquityChart';
import { SmallSampleNotice } from '../components/SmallSampleNotice';
import { TimeModeProvider } from '../lib/time';

describe('courbe en R', () => {
  it('choisit des graduations rondes qui incluent zéro', () => {
    expect(niceScale(-10.87, 0)).toEqual({ min: -15, max: 0, ticks: [-15, -10, -5, 0] });
    const s = niceScale(-10.87, 0);
    expect(s.ticks).toContain(0);
    expect(s.min).toBeLessThanOrEqual(-10.87);
    expect(s.ticks.every((t) => Number.isInteger(t / (s.ticks[1]! - s.ticks[0]!)))).toBe(true);
    expect(niceScale(0, 0.2).ticks).toEqual([-0.5, 0, 0.5, 1]);
  });

  it("affiche un état vide sans trade terminé et le total sinon", () => {
    const { rerender } = render(
      <TimeModeProvider initial="utc">
        <EquityChart points={[]} />
      </TimeModeProvider>,
    );
    expect(screen.getByText('Pas encore de courbe')).toBeInTheDocument();
    rerender(
      <TimeModeProvider initial="utc">
        <EquityChart
          points={[
            { time: '2026-01-17T09:10:00Z', r: -1.08 },
            { time: '2026-01-18T12:15:00Z', r: 0.92 },
          ]}
        />
      </TimeModeProvider>,
    );
    expect(screen.getByRole('img')).toHaveAccessibleName(/\+0,92 R après 2 trades/);
  });

  it('rappelle que moins de 100 trades ne permettent pas de conclure', () => {
    const { rerender } = render(<SmallSampleNotice trades={12} />);
    expect(screen.getByText(/échantillon trop petit pour conclure/)).toBeInTheDocument();
    rerender(<SmallSampleNotice trades={100} />);
    expect(screen.queryByText(/échantillon trop petit/)).not.toBeInTheDocument();
  });
});
