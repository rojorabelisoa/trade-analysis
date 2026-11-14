# Architecture du bot SR-Liq

## Vue d'ensemble

```
sr-liq-bot/
├── engine/   Moteur de stratégie + backtest. Java 21 pur, aucune dépendance Spring.
├── app/      (phase 3+) Application Spring Boot : données, paper trading, Telegram, API REST.
├── web/      (phase 6) Interface React + TypeScript (Vite), embarquée dans le jar avec -PwithWeb.
├── tools/parity/  Script Python qui génère les fixtures de parité depuis `develop`.
└── docs/
```

Outil de build : **Gradle** (Kotlin DSL, wrapper commité). Un seul build multi-modules
gère le moteur, l'application et, plus tard, l'assemblage du front.

## Principe : un seul moteur, vérifié contre la référence

Le module `engine` est un portage fidèle du moteur Python de la branche `develop`.
Les **tests de parité** rejouent 7 scénarios (fixtures dans
`engine/src/test/resources/parity/`) et exigent les mêmes signaux et les mêmes trades
que le Python (prix à 1e-6 près). Toute évolution de la stratégie se fait d'abord
dans la référence, puis les fixtures sont régénérées :

```
git worktree add ../reference origin/develop
python3 tools/parity/generate_fixtures.py ../reference
```

Le moteur est une fonction pure : bougies + configuration + biais + annonces → signaux.
Il ne connaît ni la base, ni le réseau, ni l'horloge. En direct, l'application le
relance à chaque clôture de bougie sur une fenêtre glissante et ne garde que le signal
de la dernière bougie : il n'existe donc qu'**une** implémentation de la stratégie,
la même en backtest et en direct.

## Paquets du module `engine` (`com.srliq.engine`)

| Paquet | Rôle | Référence Python |
|---|---|---|
| `model` | `Candle`, `CandleSeries`, `Direction`, `Signal`, `Trade`, `Outcome` | — |
| `data` | lecture CSV TradingView, agrégation en H1/H4 | `data.py` |
| `indicator` | ATR (Wilder), EMA | `structure.py` |
| `structure` | pivots confirmés, zones S/R, order blocks, tendance | `structure.py`, `strategy.py` |
| `filter` | biais fondamental, annonces, sessions | `filters.py` |
| `strategy` | `StrategyConfig`, `SignalEngine` | `strategy.py` |
| `backtest` | `IntradayRules`, `Backtester`, `BacktestSummary` | `backtest.py` |

## Module `app` (`com.srliq.app`, phase 3)

Spring Boot 3.5, PostgreSQL + Flyway (`app/src/main/resources/db/migration`), accès aux
données en JDBC simple (`JdbcClient`) : upserts `ON CONFLICT` explicites, pas de JPA.

| Paquet | Rôle |
|---|---|
| `marketdata` | `MarketDataSource` (pousse des bougies clôturées vers un `CandleSink`), import CSV TradingView, stockage idempotent |
| `signal` | calcul (bougies LTF → H1/H4 → `SignalEngine`), enregistrement sans doublon, `SignalRecordedEvent` |
| `webhook` | alertes TradingView `POST /api/webhooks/tradingview/{secret}` |
| `bias`, `news`, `instrument` | API de saisie et de lecture |
| `paper` (phase 4) | paper trading = rejeu déterministe de `Backtester.run` sur l'historique stocké après chaque calcul de signaux ; trades OPEN/CLOSED, dimensionnement à capital fixe, `/api/paper/*` |
| `trading` | coupe-circuit global (périodes d'arrêt), `/api/trading/halt|resume|status` |
| `backtest` | backtest à la demande sur un CSV, en mémoire (`POST /api/backtests`) |
| `broker` | interface `BrokerAdapter`, `PaperBrokerAdapter` (journalise seulement) ; `trading.live.enabled=true` empêche le démarrage |
| `notification` (phase 5) | Telegram (`sendMessage`) : signaux récents, trades simulés clôturés, coupe-circuit ; suivi et renvoi `/api/notifications` |

| `web` (phase 6) | `SpaWebConfig` : sert le front embarqué (`classpath:/static/`), repli sur `index.html` pour les routes du front, jamais pour `/api/**` ni `/actuator/**` |

Démarrage local : `cp .env.example .env` (renseigner les valeurs), `docker compose up -d`,
puis `set -a; . ./.env; set +a; ./gradlew :app:bootRun`. Les tests de `app` utilisent un vrai
PostgreSQL (`SRLIQ_TEST_DB_URL`, défaut `jdbc:postgresql://localhost:5432/srliq_test`, `srliq`/`srliq`).

## Module `web` (phase 6, interface)

React 19 + TypeScript + Vite, sans framework CSS ni bibliothèque de graphiques (la courbe en R est
un SVG). Dépendances d'exécution : `react`, `react-dom`, `react-router`. Tests : Vitest +
Testing Library (jsdom).

| Dossier | Rôle |
|---|---|
| `src/api/types.ts` | types alignés sur les records Java renvoyés par l'API (formes JSON réelles) |
| `src/api/client.ts` | `fetch` sur des chemins relatifs `/api/...` ; erreurs `problem+json` → `ApiError` (message `detail`, liste `errors`) |
| `src/lib/format.ts` | nombres, R, devises, heures (fuseau du navigateur ou UTC), libellés en français, biais en vigueur (même règle que `BiasBook`) |
| `src/lib/useApi.ts`, `src/lib/time.tsx` | états chargement / erreur / données ; choix heure locale / UTC (mémorisé dans le navigateur) |
| `src/components/` | cartes, états vides et erreurs, coupe-circuit (confirmation dans la page), courbe en R, statistiques |
| `src/pages/` | tableau de bord, signaux, paper trading, backtest, fondamental, données, notifications |

Écrans → endpoints : tableau de bord (`/api/trading/status|halt|resume`, `/api/bias`,
`/api/news?from=`, `/api/signals`, `/api/paper/trades?status=OPEN`, `/api/paper/summary`) ;
signaux (`/api/signals`) ; paper trading (`/api/paper/summary|trades|replay`) ; backtest
(`POST /api/backtests`) ; fondamental (`/api/bias`, `/api/news`, `POST /api/signals/recompute`,
un appel par instrument pour « tous ») ; données (`POST /api/candles/import`) ; notifications
(`/api/notifications`, `POST /api/notifications/{id}/retry`). Le biais en vigueur est calculé côté
front à partir de `GET /api/bias` (aucun endpoint dédié). Pas d'entonnoir de diagnostic : le moteur
n'en produit pas.

Livraison : en développement, `npm run dev` (port 5173) relaie `/api` et `/actuator` vers le port
8080. En un seul fichier, `./gradlew :app:bootJar -PwithWeb` lance `npm ci` puis `npm run build`
et copie `web/dist` dans `classpath:/static/` ; sans `-PwithWeb`, Gradle n'appelle jamais Node.
L'API n'a pas d'authentification : l'application ne doit pas être exposée telle quelle sur Internet.

## Règles non négociables

- **Aucun look-ahead** : un pivot n'existe qu'à la clôture des bougies qui le
  confirment ; une bougie H1/H4 n'est utilisée qu'une fois clôturée.
- **Heures en UTC** partout (`Instant`).
- **Paper trading par défaut** ; aucun ordre réel sans `trading.live.enabled=true`,
  un adaptateur courtier configuré et un coupe-circuit.
- Aucun secret dans le dépôt.

## Écart connu avec TradingView

Les bougies H4 sont agrégées à partir de minuit UTC (00:00, 04:00…), comme dans la
référence Python. TradingView découpe les H4 des futures CME à partir de l'ouverture
de séance. Les signaux du bot peuvent donc différer légèrement de ceux du script Pine.
À traiter quand le flux temps réel sera choisi.
