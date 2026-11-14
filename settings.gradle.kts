rootProject.name = "sr-liq-bot"

// engine : moteur de stratégie et backtest, Java pur (aucune dépendance Spring).
// app (phase 3+) : application Spring Boot. web (phase 6) : interface React.
include("engine")
include("app")
