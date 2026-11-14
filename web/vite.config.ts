/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Évite une dépendance à @types/node pour une seule variable.
declare const process: { env: Record<string, string | undefined> };

// En développement, /api et /actuator sont relayés vers le backend Spring Boot (port 8080).
const backend = process.env.SRLIQ_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/actuator': backend,
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
