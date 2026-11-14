-- Schéma initial du bot SR-Liq : instruments, bougies, signaux, biais fondamental, annonces.
-- Toutes les heures sont en UTC (timestamptz) ; les prix en double precision, comme le moteur.

CREATE TABLE instrument (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol      varchar(16)      NOT NULL UNIQUE,
    name        varchar(100)     NOT NULL,
    point_value double precision NOT NULL CHECK (point_value > 0)  -- $ par point de prix et par contrat
);

INSERT INTO instrument (symbol, name, point_value) VALUES
    ('GC',  'Or (COMEX Gold)',             100),
    ('MGC', 'Micro Or (COMEX Micro Gold)', 10),
    ('SI',  'Argent (COMEX Silver)',       5000),
    ('HG',  'Cuivre (COMEX Copper)',       25000),
    ('CL',  'Pétrole brut (NYMEX WTI)',    1000);

-- Bougies du timeframe d'entrée, heure d'OUVERTURE en UTC. Seules des bougies clôturées sont stockées.
CREATE TABLE candle (
    instrument_id     bigint           NOT NULL REFERENCES instrument (id),
    timeframe_seconds integer          NOT NULL CHECK (timeframe_seconds > 0),
    open_time         timestamptz      NOT NULL,
    open              double precision NOT NULL,
    high              double precision NOT NULL,
    low               double precision NOT NULL,
    close             double precision NOT NULL,
    volume            double precision NOT NULL DEFAULT 0,
    CONSTRAINT candle_pk PRIMARY KEY (instrument_id, timeframe_seconds, open_time)
);

-- Signaux : champs de engine.model.Signal + provenance. L'unicité (instrument, source, heure, sens)
-- rend l'enregistrement idempotent (recalcul, alerte rejouée).
CREATE TABLE signal (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id bigint           NOT NULL REFERENCES instrument (id),
    source        varchar(16)      NOT NULL CHECK (source IN ('ENGINE', 'TRADINGVIEW')),
    timeframe     varchar(32)      NOT NULL,                       -- "5m" (moteur) ou champ "tf" brut de l'alerte
    signal_time   timestamptz      NOT NULL,            -- clôture de la bougie de confirmation
    direction     varchar(8)       NOT NULL CHECK (direction IN ('long', 'short')),
    entry         double precision NOT NULL,
    stop          double precision NOT NULL,
    target        double precision NOT NULL,
    rr            double precision NOT NULL,
    zone          varchar(200),
    contacts      integer,                              -- alerte TradingView uniquement
    sweep_time    timestamptz,                          -- moteur uniquement
    sweep_extreme double precision,
    bias          varchar(16),
    session       varchar(32),
    next_zone     double precision,
    received_at   timestamptz      NOT NULL DEFAULT now(),
    CONSTRAINT signal_unique UNIQUE (instrument_id, source, timeframe, signal_time, direction)
);

CREATE INDEX signal_time_idx ON signal (signal_time DESC);

-- Biais fondamental : s'applique de sa date (minuit UTC) jusqu'à la ligne suivante du même instrument.
CREATE TABLE fundamental_bias (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    effective_date date         NOT NULL,
    instrument_id  bigint       NOT NULL REFERENCES instrument (id),
    bias           varchar(8)   NOT NULL CHECK (bias IN ('long', 'short', 'neutral', 'off')),
    note           varchar(500),
    CONSTRAINT fundamental_bias_unique UNIQUE (instrument_id, effective_date)
);

-- Annonces économiques (heure UTC). Seules les annonces "high" bloquent les entrées.
CREATE TABLE news_event (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_time  timestamptz  NOT NULL,
    currency    varchar(8)   NOT NULL,
    impact      varchar(8)   NOT NULL CHECK (impact IN ('low', 'medium', 'high')),
    event       varchar(200) NOT NULL
);

CREATE INDEX news_event_time_idx ON news_event (event_time);
