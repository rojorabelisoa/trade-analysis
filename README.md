# Bot SR-Liq (Spring Boot + React)

Bot de trading intraday sur futures de matières premières qui applique la stratégie
**Tendance H4 → Zone S/R H1 → Liquidation → Entrée (CHOCH) → Objectif order block**.

Le bot détecte les signaux, les simule (**paper trading** : aucun ordre réel n'est envoyé),
les envoie sur Telegram et affiche tout dans une interface web utilisable sur téléphone.

> La stratégie n'a pas encore prouvé qu'elle gagne. Les résultats de backtest et de paper
> trading servent à **mesurer**, ils ne promettent aucun gain.

- Le travail se fait sur cette branche, `bot-trading`.
- La stratégie de référence (moteur Python, script Pine TradingView, backtests) est sur
  la branche `develop`.
- Le prompt pour l'orchestrateur (Claude Fable 5.2) est dans
  `docs/prompt-orchestrateur-fable.md`. L'agent exécutant (Claude Opus 5.5) est défini
  dans `.claude/agents/executor.md`. L'architecture est décrite dans `docs/architecture.md`.

## Avertissement de sécurité

L'application **n'a pas d'authentification** : toute personne qui peut ouvrir son adresse peut
arrêter le trading, modifier le biais ou supprimer des annonces. **Ne l'exposez pas telle quelle
sur Internet.** Par défaut, elle n'écoute que sur l'ordinateur où elle tourne. Pour l'ouvrir depuis un téléphone
sur le même Wi-Fi, lancez-la avec `SERVER_ADDRESS=0.0.0.0`, uniquement sur un réseau de confiance.
Pour la consulter depuis votre téléphone hors de chez vous, il faudra d'abord ajouter une protection (VPN, accès protégé par mot
de passe) : c'est une décision d'hébergement à prendre ensemble.

Les secrets (mot de passe de la base, secret du webhook TradingView, jeton Telegram) se mettent
uniquement dans le fichier `.env`, jamais dans le code. L'interface ne les affiche jamais.

## Démarrer en local, pas à pas

### 1. Installer les outils (une seule fois)

| Outil | Pour quoi faire | Vérifier |
|---|---|---|
| Java 21 (par exemple Temurin) | faire tourner le bot | `java -version` affiche 21 |
| Docker Desktop | faire tourner la base de données PostgreSQL | `docker --version` |
| Node.js 20 ou plus récent | seulement pour modifier ou reconstruire l'interface | `node -v` |
| Git | récupérer le code | `git --version` |

### 2. Récupérer le code

```bash
git clone https://github.com/rojorabelisoa/trade-analysis.git
cd trade-analysis
git checkout bot-trading
```

### 3. Préparer la configuration

```bash
cp .env.example .env
```

Ouvrez `.env` avec un éditeur de texte et remplacez `change-me` par un mot de passe de votre
choix pour la base. Les lignes Telegram et webhook peuvent rester vides au début.

### 4. Démarrer la base de données

```bash
docker compose up -d
```

### 5. Construire l'application avec son interface (un seul fichier)

```bash
./gradlew :app:bootJar -PwithWeb
```

(Sous Windows : `gradlew.bat :app:bootJar -PwithWeb`.) La première fois, cela télécharge les
dépendances et prend quelques minutes. Le résultat est le fichier
`app/build/libs/app-0.1.0-SNAPSHOT.jar`, qui contient le bot **et** l'interface.

### 6. Lancer l'application

```bash
set -a; . ./.env; set +a
java -jar app/build/libs/app-0.1.0-SNAPSHOT.jar
```

Attendez la ligne `Started SrLiqApplication`, puis ouvrez **http://localhost:8080** dans votre
navigateur. Pour l'arrêter : `Ctrl+C` dans le terminal.

### 7. Premiers pas dans l'interface

1. **Données** : choisissez l'instrument (par exemple GC) et un export CSV de TradingView
   (bougies 5 ou 15 minutes, menu du graphique → « Exporter les données du graphique »). Pour un
   essai, utilisez `engine/src/test/resources/parity/candles/demo_5m.csv` (données de démonstration).
   Les signaux et le paper trading sont calculés automatiquement après l'import.
2. **Tableau de bord** : coupe-circuit, biais en vigueur, prochaine annonce, derniers signaux,
   positions simulées et résultats en R.
3. **Fondamental** : saisissez le biais par instrument et les annonces de la semaine, puis
   cliquez sur « Recalculer les signaux » (les modifications ne recalculent rien toutes seules).
4. **Backtest** : envoyez un CSV pour tester la stratégie sans rien enregistrer.

Les heures sont affichées dans le fuseau de votre navigateur ; le bouton « UTC » du menu bascule
en heure UTC (survolez une heure pour voir l'autre fuseau).

### Notifications Telegram (facultatif)

Créez un bot avec @BotFather, puis renseignez `SRLIQ_TELEGRAM_BOT_TOKEN` et
`SRLIQ_TELEGRAM_CHAT_ID` dans `.env` et relancez l'application. L'écran « Notifications » montre
ce qui a été envoyé, ce qui ne l'a pas été et pourquoi.

## Pour les développeurs

| Commande | Effet |
|---|---|
| `./gradlew test` | tests du moteur et de l'application (PostgreSQL requis, base `srliq_test`) |
| `./gradlew :app:bootRun` | backend seul sur le port 8080 (variables de `.env` chargées au préalable) |
| `./gradlew :app:bootJar` | jar du backend **sans** interface (n'a pas besoin de Node) |
| `./gradlew :app:bootJar -PwithWeb` | jar avec l'interface (lance `npm ci` et `npm run build` dans `web/`) |

Interface seule (`web/`, React + TypeScript + Vite), backend démarré à côté :

```bash
cd web
npm install        # une fois
npm run dev        # http://localhost:5173, /api relayé vers http://localhost:8080
npm run test       # tests (Vitest + Testing Library)
npm run typecheck  # vérification des types
npm run build      # fichiers statiques dans web/dist
```

Pour relayer vers un autre backend : `SRLIQ_BACKEND_URL=http://autre-hote:8080 npm run dev`.
