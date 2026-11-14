import { useEffect, useRef, useState, type PointerEvent } from 'react';
import type { EquityPoint } from '../api/types';
import { formatDateTime, formatR } from '../lib/format';
import { useTimeMode } from '../lib/time';
import { EmptyState } from './StateViews';

const HEIGHT = 200;
const PAD = { top: 12, right: 12, bottom: 26, left: 48 };

/** Largeur du conteneur, suivie au redimensionnement (le texte du SVG n'est jamais étiré). */
function useWidth<T extends HTMLElement>(fallback = 600) {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(fallback);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const update = () => setWidth(Math.max(240, Math.floor(el.clientWidth)));
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return { ref, width };
}

/** Pas entre deux graduations. */
function step(ticks: number[]): number {
  return ticks.length > 1 ? Math.abs(ticks[1]! - ticks[0]!) : 1;
}

/** Graduations « rondes » (1, 2 ou 5 × 10^k) couvrant [lo, hi], environ 4 intervalles. */
export function niceScale(lo: number, hi: number): { min: number; max: number; ticks: number[] } {
  if (hi - lo < 1) {
    const mid = (hi + lo) / 2;
    lo = mid - 0.5;
    hi = mid + 0.5;
  }
  const raw = (hi - lo) / 4;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const unit = [1, 2, 5, 10].map((m) => m * mag).find((u) => u >= raw) ?? 10 * mag;
  // « + 0 » transforme un éventuel −0 en 0.
  const min = Math.floor(lo / unit) * unit + 0;
  const max = Math.ceil(hi / unit) * unit + 0;
  const ticks: number[] = [];
  for (let v = min; v <= max + unit / 2; v += unit) ticks.push(Number(v.toFixed(6)) + 0);
  return { min, max, ticks };
}

/**
 * Courbe cumulée en R : un point par trade terminé, dans l'ordre des sorties, en partant de 0.
 * Survol (ou toucher) : valeur et heure du trade le plus proche.
 */
export function EquityChart({ points, title = 'Courbe cumulée en R' }: { points: EquityPoint[]; title?: string }) {
  const { ref, width } = useWidth<HTMLDivElement>();
  const { mode } = useTimeMode();
  const [hover, setHover] = useState<number | null>(null);

  if (points.length === 0) {
    return (
      <EmptyState title="Pas encore de courbe">La courbe apparaît dès qu'un trade est terminé.</EmptyState>
    );
  }

  const values = [0, ...points.map((p) => p.r)];
  const { min, max, ticks } = niceScale(Math.min(...values), Math.max(...values));

  const innerW = width - PAD.left - PAD.right;
  const innerH = HEIGHT - PAD.top - PAD.bottom;
  const n = values.length;
  const x = (i: number) => PAD.left + (n === 1 ? innerW / 2 : (i / (n - 1)) * innerW);
  const y = (v: number) => PAD.top + ((max - v) / (max - min)) * innerH;
  const path = values.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const last = values[n - 1]!;

  const onMove = (e: PointerEvent<SVGRectElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const ratio = innerW === 0 ? 0 : px / rect.width;
    const i = Math.round(ratio * (n - 1));
    setHover(Math.min(n - 1, Math.max(1, i)));
  };

  const hoverPoint = hover !== null ? points[hover - 1] : undefined;
  const tipLeft = hover !== null ? Math.min(Math.max(x(hover), 70), width - 70) : 0;

  return (
    <figure className="chart" aria-label={title}>
      <div ref={ref} className="chart-area">
        <svg width={width} height={HEIGHT} role="img" aria-label={`${title} : ${formatR(last)} après ${points.length} trades`}>
          {ticks.map((v) => (
            <g key={v} className="chart-grid">
              <line x1={PAD.left} x2={width - PAD.right} y1={y(v)} y2={y(v)} />
              <text x={PAD.left - 6} y={y(v)} dy="0.32em" textAnchor="end">
                {formatR(v, step(ticks) < 1 ? 1 : 0)}
              </text>
            </g>
          ))}
          {min < 0 && max > 0 && (
            <line className="chart-zero" x1={PAD.left} x2={width - PAD.right} y1={y(0)} y2={y(0)} />
          )}
          <path className="chart-line" d={path} />
          <circle className="chart-dot" cx={x(n - 1)} cy={y(last)} r={4} />
          <text className="chart-axis" x={PAD.left} y={HEIGHT - 6} textAnchor="start">
            {formatDateTime(points[0]!.time, mode).slice(0, 10)}
          </text>
          <text className="chart-axis" x={width - PAD.right} y={HEIGHT - 6} textAnchor="end">
            {formatDateTime(points[points.length - 1]!.time, mode).slice(0, 10)}
          </text>
          {hover !== null && (
            <g>
              <line className="chart-cross" x1={x(hover)} x2={x(hover)} y1={PAD.top} y2={HEIGHT - PAD.bottom} />
              <circle className="chart-dot chart-dot-hover" cx={x(hover)} cy={y(values[hover]!)} r={5} />
            </g>
          )}
          <rect
            x={PAD.left}
            y={PAD.top}
            width={Math.max(0, innerW)}
            height={innerH}
            fill="transparent"
            onPointerMove={onMove}
            onPointerDown={onMove}
            onPointerLeave={() => setHover(null)}
          />
        </svg>
        {hoverPoint && (
          <div className="chart-tip" style={{ left: tipLeft }}>
            <strong>{formatR(hoverPoint.r)}</strong>
            <span>
              Trade {hover} · {formatDateTime(hoverPoint.time, mode)}
            </span>
          </div>
        )}
      </div>
      <figcaption className="muted small">
        {points.length} trade{points.length > 1 ? 's' : ''} terminé{points.length > 1 ? 's' : ''}, total {formatR(last)}.
        Chaque point est le cumul en R après la sortie d'un trade.
      </figcaption>
      <details className="chart-table">
        <summary>Voir les valeurs</summary>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Sortie</th>
                <th className="num">Cumul</th>
              </tr>
            </thead>
            <tbody>
              {points.map((p, i) => (
                <tr key={`${p.time}-${i}`}>
                  <td>{i + 1}</td>
                  <td>{formatDateTime(p.time, mode)}</td>
                  <td className="num">{formatR(p.r)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
