-- Phase 4 (paper trading) et phase 5 (notifications).

-- Coupe-circuit global : périodes d'arrêt [started_at, ended_at). ended_at NULL = arrêt en cours.
-- Les signaux dont l'heure tombe dans une période d'arrêt n'ouvrent aucun trade simulé.
CREATE TABLE trading_halt (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at timestamptz  NOT NULL,
    ended_at   timestamptz,
    reason     varchar(500) NOT NULL,
    CONSTRAINT trading_halt_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);

-- Au plus une période d'arrêt ouverte à la fois.
CREATE UNIQUE INDEX trading_halt_single_open ON trading_halt ((true)) WHERE ended_at IS NULL;

-- Trades simulés : rejeu déterministe du backtest du moteur sur l'historique stocké.
-- Un trade OPEN est recalculé à chaque rejeu (il manque des bougies pour le terminer).
CREATE TABLE paper_trade (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id  bigint           NOT NULL REFERENCES instrument (id),
    timeframe      varchar(32)      NOT NULL,
    signal_id      bigint           NOT NULL REFERENCES signal (id),
    signal_time    timestamptz      NOT NULL,
    direction      varchar(8)       NOT NULL CHECK (direction IN ('long', 'short')),
    status         varchar(8)       NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    entry_time     timestamptz      NOT NULL,
    entry          double precision NOT NULL,             -- ouverture de la bougie qui suit le signal
    stop           double precision NOT NULL,
    target         double precision NOT NULL,
    risk_points    double precision NOT NULL CHECK (risk_points > 0),
    exit_time      timestamptz,                           -- NULL tant que le trade est ouvert
    exit_price     double precision,
    outcome        varchar(8) CHECK (outcome IN ('tp', 'sl', 'timeout', 'eod')),
    last_time      timestamptz      NOT NULL,             -- dernière bougie prise en compte
    last_price     double precision NOT NULL,             -- prix de sortie, ou dernière clôture si OPEN
    r              double precision NOT NULL,             -- résultat (ou résultat latent) en R, coût déduit
    cost           double precision NOT NULL,             -- coût par trade, en points de prix
    quantity       integer          NOT NULL CHECK (quantity >= 0),
    point_value    double precision NOT NULL,
    pnl_currency   double precision NOT NULL,             -- r x risque x valeur du point x quantité
    risk_too_large boolean          NOT NULL,             -- risque d'un contrat > budget de risque
    created_at     timestamptz      NOT NULL,
    updated_at     timestamptz      NOT NULL,
    CONSTRAINT paper_trade_unique UNIQUE (instrument_id, timeframe, signal_time, direction),
    CONSTRAINT paper_trade_closed CHECK (status = 'OPEN'
        OR (exit_time IS NOT NULL AND exit_price IS NOT NULL AND outcome IS NOT NULL))
);

CREATE INDEX paper_trade_time_idx ON paper_trade (signal_time DESC);

-- Notifications envoyées (ou non) : une ligne par sujet et par canal.
-- kind = SIGNAL (subject_id = signal.id), TRADE_CLOSED (paper_trade.id), HALT / RESUME (trading_halt.id).
CREATE TABLE notification (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind       varchar(16)  NOT NULL CHECK (kind IN ('SIGNAL', 'TRADE_CLOSED', 'HALT', 'RESUME')),
    subject_id bigint       NOT NULL,
    channel    varchar(16)  NOT NULL CHECK (channel IN ('TELEGRAM')),
    status     varchar(8)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    reason     varchar(500),
    message    text,
    attempts   integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT notification_unique UNIQUE (kind, subject_id, channel)
);

CREATE INDEX notification_created_idx ON notification (created_at DESC);
