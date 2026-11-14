import { useEffect, useState } from 'react';
import { NavLink, Route, Routes, useLocation } from 'react-router';
import { useTimeMode } from './lib/time';
import { BacktestPage } from './pages/BacktestPage';
import { DashboardPage } from './pages/DashboardPage';
import { DataPage } from './pages/DataPage';
import { FundamentalPage } from './pages/FundamentalPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { PaperPage } from './pages/PaperPage';
import { SignalsPage } from './pages/SignalsPage';

const LINKS = [
  { to: '/', label: 'Tableau de bord', end: true },
  { to: '/signaux', label: 'Signaux' },
  { to: '/paper', label: 'Paper trading' },
  { to: '/backtest', label: 'Backtest' },
  { to: '/fondamental', label: 'Fondamental' },
  { to: '/donnees', label: 'Données' },
  { to: '/notifications', label: 'Notifications' },
];

function TimeToggle() {
  const { mode, setMode, zone } = useTimeMode();
  return (
    <div className="time-toggle" role="group" aria-label="Fuseau des heures affichées">
      <button type="button" aria-pressed={mode === 'local'} onClick={() => setMode('local')} title={zone}>
        Heure locale
      </button>
      <button type="button" aria-pressed={mode === 'utc'} onClick={() => setMode('utc')}>
        UTC
      </button>
    </div>
  );
}

export function App() {
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const { mode, zone } = useTimeMode();

  useEffect(() => {
    setMenuOpen(false);
    window.scrollTo?.(0, 0);
  }, [location.pathname]);

  return (
    <div className="layout">
      <header className="topbar">
        <NavLink className="brand" to="/">
          <span className="brand-mark" aria-hidden="true" />
          SR-Liq
        </NavLink>
        <span className="mode-pill" title="Aucun ordre réel n'est envoyé">
          Paper trading
        </span>
        <button
          type="button"
          className="menu-btn"
          aria-expanded={menuOpen}
          aria-controls="main-nav"
          onClick={() => setMenuOpen((o) => !o)}
        >
          {menuOpen ? 'Fermer' : 'Menu'}
        </button>
      </header>
      <nav id="main-nav" className={`sidenav ${menuOpen ? 'open' : ''}`} aria-label="Navigation principale">
        <ul>
          {LINKS.map((l) => (
            <li key={l.to}>
              <NavLink to={l.to} end={l.end}>
                {l.label}
              </NavLink>
            </li>
          ))}
        </ul>
        <TimeToggle />
        <p className="muted small nav-zone">
          Heures affichées : {mode === 'utc' ? 'UTC' : `heure locale (${zone})`}. Survolez une heure pour l'autre
          fuseau.
        </p>
      </nav>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/signaux" element={<SignalsPage />} />
          <Route path="/paper" element={<PaperPage />} />
          <Route path="/backtest" element={<BacktestPage />} />
          <Route path="/fondamental" element={<FundamentalPage />} />
          <Route path="/donnees" element={<DataPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route
            path="*"
            element={
              <div className="card">
                <h1>Page introuvable</h1>
                <p>
                  Cette adresse n'existe pas. <NavLink to="/">Revenir au tableau de bord</NavLink>.
                </p>
              </div>
            }
          />
        </Routes>
      </main>
    </div>
  );
}
