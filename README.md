# Bot SR-Liq (Spring Boot + React)

Bot de trading intraday sur futures de matières premières qui applique la stratégie
**Tendance H4 → Zone S/R H1 → Liquidation → Entrée (CHOCH) → Objectif order block**.

- Le travail se fait sur cette branche, `bot-trading`.
- La stratégie de référence (moteur Python, script Pine TradingView, backtests) est sur
  la branche `develop`.
- Le prompt pour l'orchestrateur (Claude Fable 5.2) est dans
  `docs/prompt-orchestrateur-fable.md`. L'agent exécutant (Claude Opus 5.5) est défini
  dans `.claude/agents/executor.md`.
