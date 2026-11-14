// Types alignés sur les réponses réelles du backend (records Java du module app).
// Les heures sont des chaînes ISO 8601 en UTC ("2026-01-31T20:50:00Z").
// Les énumérations Java dont toString() est surchargé (Direction, Outcome, modes de stratégie)
// sont écrites en minuscules (spring.jackson.serialization.write-enums-using-to-string).

export type Direction = 'long' | 'short';
export type Outcome = 'tp' | 'sl' | 'timeout' | 'eod';
export type BiasValue = 'long' | 'short' | 'neutral' | 'off';
export type Impact = 'low' | 'medium' | 'high';
export type SignalSource = 'ENGINE' | 'TRADINGVIEW';
export type PaperTradeStatus = 'OPEN' | 'CLOSED';
export type NotificationKind = 'SIGNAL' | 'TRADE_CLOSED' | 'HALT' | 'RESUME';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'SKIPPED';

/** Page de résultats (SignalRepository.Page, PaperTradeRepository.Page, NotificationRepository.Page). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

/** instrument.Instrument */
export interface Instrument {
  id: number;
  symbol: string;
  name: string;
  pointValue: number;
}

/** signal.SignalRecord */
export interface SignalRecord {
  id: number;
  symbol: string;
  source: SignalSource;
  timeframe: string;
  time: string;
  direction: Direction;
  entry: number;
  stop: number;
  target: number;
  rr: number;
  zone: string | null;
  contacts: number | null;
  sweepTime: string | null;
  sweepExtreme: number | null;
  bias: string | null;
  session: string | null;
  nextZone: number | null;
  receivedAt: string;
}

/** signal.RecomputeResult */
export interface RecomputeResult {
  symbol: string;
  timeframes: {
    timeframe: string;
    candles: number;
    generated: number;
    recorded: number;
    skipped: string | null;
  }[];
}

/** marketdata.IngestResult */
export interface IngestResult {
  symbol: string;
  timeframe: string;
  received: number;
  skippedOpen: number;
  inserted: number;
  updated: number;
  unchanged: number;
  signals: RecomputeResult | null;
}

/** bias.BiasEntry */
export interface BiasEntry {
  id: number;
  effectiveDate: string; // AAAA-MM-JJ
  symbol: string;
  bias: BiasValue;
  note: string | null;
}

/** Corps de POST /api/bias (BiasController.CreateBias). */
export interface CreateBias {
  date: string;
  symbol: string;
  bias: BiasValue;
  note?: string;
}

/** news.NewsEvent */
export interface NewsEvent {
  id: number;
  time: string;
  currency: string;
  impact: Impact;
  event: string;
}

/** Corps de POST /api/news (NewsController.CreateNews). */
export interface CreateNews {
  time: string;
  currency: string;
  impact: Impact;
  event: string;
}

/** trading.TradingHalt */
export interface TradingHalt {
  id: number;
  startedAt: string;
  endedAt: string | null;
  reason: string;
}

/** trading.TradingController.Status */
export interface TradingStatus {
  halted: boolean;
  since: string | null;
  reason: string | null;
  paperTrading: boolean;
  liveTradingEnabled: boolean;
  broker: string;
  history: TradingHalt[];
}

/** backtest.TradeStatistics.SummaryView */
export interface SummaryView {
  trades: number;
  winRatePct: number | null;
  expectancyR: number | null;
  totalR: number | null;
  profitFactor: number | null;
  noLosses: boolean;
  maxDrawdownR: number | null;
  worstLosingStreak: number;
  /** "objectif/stop/fin de journée", ex. "0/11/1" */
  exitsTpSlEod: string;
  avgDurationMin: number | null;
}

/** engine.backtest.Breakdown.Row */
export interface BreakdownRow {
  key: string;
  trades: number;
  winRatePct: number;
  expectancyR: number;
  totalR: number;
}

/** backtest.TradeStatistics.EquityPoint */
export interface EquityPoint {
  time: string;
  r: number;
}

/** paper.PaperController.PaperSummary */
export interface PaperSummary {
  symbol: string | null;
  timeframe: string | null;
  closedTrades: number;
  openTrades: number;
  pnlCurrency: number;
  capital: number;
  riskPercent: number;
  summary: SummaryView;
  bySession: BreakdownRow[];
  byDirection: BreakdownRow[];
  byOutcome: BreakdownRow[];
  equityCurve: EquityPoint[];
}

/** paper.PaperTrade */
export interface PaperTrade {
  id: number;
  symbol: string;
  timeframe: string;
  signalId: number;
  signalTime: string;
  direction: Direction;
  status: PaperTradeStatus;
  entryTime: string;
  entry: number;
  stop: number;
  target: number;
  riskPoints: number;
  exitTime: string | null;
  exitPrice: number | null;
  outcome: Outcome | null;
  lastTime: string;
  lastPrice: number;
  r: number;
  cost: number;
  quantity: number;
  pointValue: number;
  pnlCurrency: number;
  riskTooLarge: boolean;
  session: string | null;
  bias: string | null;
  createdAt: string;
  updatedAt: string;
}

/** paper.PaperTradingService.ReplayResult */
export interface ReplayResult {
  symbol: string;
  timeframes: {
    timeframe: string;
    candles: number;
    signals: number;
    excludedByHalt: number;
    trades: number;
    open: number;
    inserted: number;
    updated: number;
    deleted: number;
    unchanged: number;
    skipped: string | null;
  }[];
}

/** notification.Notification */
export interface Notification {
  id: number;
  kind: NotificationKind;
  subjectId: number;
  channel: string;
  status: NotificationStatus;
  reason: string | null;
  message: string | null;
  attempts: number;
  createdAt: string;
  updatedAt: string;
}

/** engine.backtest.IntradayRules */
export interface IntradayRules {
  entryStartH: number;
  entryEndH: number;
  flatH: number;
  flatM: number;
  maxTradesDay: number;
  maxLossDayR: number;
}

export type TrendMode = 'structure' | 'strict' | 'ema' | 'off';
export type TargetMode = 'ob' | 'h4' | 'rr';
export type StopMode = 'sweep' | 'h4';

/** Réglages de stratégie modifiables depuis l'écran de backtest (clés de srliq.strategy en camelCase). */
export interface StrategyOverrides {
  trendMode?: TrendMode;
  targetMode?: TargetMode;
  rr?: number;
  stopMode?: StopMode;
  minTouches?: number;
  minRr?: number;
}

/** Configuration effective renvoyée (engine.strategy.StrategyConfig) ; seuls les champs affichés sont typés. */
export interface StrategyConfigView {
  trendMode: TrendMode;
  targetMode: TargetMode;
  stopMode: StopMode;
  minTouches: number;
  minRr: number;
  rr: number;
  maxRiskHtfAtr: number;
  direction: string;
  sessions: string[] | null;
  [key: string]: unknown;
}

/** backtest.OnDemandBacktestService.TradeView */
export interface BacktestTrade {
  signalTime: string;
  direction: Direction;
  entryTime: string;
  entry: number;
  stop: number;
  target: number;
  exitTime: string;
  exit: number;
  outcome: Outcome;
  r: number;
  session: string | null;
  bias: string | null;
  zone: string | null;
  openAtEnd: boolean;
}

/** backtest.OnDemandBacktestService.SignalView */
export interface BacktestSignal {
  time: string;
  direction: Direction;
  entry: number;
  stop: number;
  target: number;
  rr: number;
  zone: string | null;
  sweepTime: string | null;
  sweepExtreme: number | null;
  bias: string | null;
  session: string | null;
  nextZone: number | null;
}

/** backtest.OnDemandBacktestService.Result */
export interface BacktestResult {
  symbol: string;
  timeframe: string;
  candles: number;
  firstCandle: string;
  lastCandle: string;
  parameters: {
    cost: number;
    intraday: boolean;
    intradayRules: IntradayRules | null;
    maxHoldBars: number;
    useBias: boolean;
    useNews: boolean;
    strategy: StrategyConfigView;
  };
  signals: BacktestSignal[];
  trades: BacktestTrade[];
  summary: SummaryView;
  bySession: BreakdownRow[];
  byDirection: BreakdownRow[];
  byOutcome: BreakdownRow[];
  equityCurve: EquityPoint[];
}

/** Erreur application/problem+json (RFC 9457) renvoyée par ApiExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: string[];
}
