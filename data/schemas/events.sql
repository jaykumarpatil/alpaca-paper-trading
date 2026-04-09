CREATE TABLE IF NOT EXISTS order_events (
  event_id UUID PRIMARY KEY,
  order_id TEXT NOT NULL,
  strategy_id TEXT NOT NULL,
  symbol TEXT NOT NULL,
  event_time TIMESTAMPTZ NOT NULL,
  state TEXT NOT NULL,
  payload JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS execution_reports (
  event_id UUID PRIMARY KEY,
  order_id TEXT NOT NULL,
  broker_order_id TEXT NOT NULL,
  status TEXT NOT NULL,
  fill_qty NUMERIC(18,6),
  fill_price NUMERIC(18,6),
  event_time TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL
);
