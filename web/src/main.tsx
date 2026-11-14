import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { TimeModeProvider } from './lib/time';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <TimeModeProvider>
        <App />
      </TimeModeProvider>
    </BrowserRouter>
  </StrictMode>,
);
