# Canonical Event Schemas (v1)

These Avro schemas define immutable event contracts for the event plane.

## Topics → Schema mapping
- `md.instruments.v1` → `InstrumentEvent.avsc`
- `md.quotes.v1` → `QuoteEvent.avsc`
- `md.trades.v1` → `TradeEvent.avsc`
- `md.bars.v1` → `BarEvent.avsc`
- `alpha.signals.v1` → `SignalEvent.avsc`
- `alpha.positionIntents.v1` → `PositionIntentEvent.avsc`
- `risk.decisions.v1` → `RiskDecisionEvent.avsc`
- `oms.orders.v1` → `OrderEvent.avsc`
- `oms.execReports.v1` → `ExecutionReportEvent.avsc`
- `portfolio.updates.v1` → `PortfolioUpdateEvent.avsc`

## Contract rules
- All events include `eventId`, `eventType`, `eventVersion`, `eventTime`, and `source`.
- Event payloads are append-only compatible.
- Producers must set Kafka key as documented in `kafka/topics.yaml`.
