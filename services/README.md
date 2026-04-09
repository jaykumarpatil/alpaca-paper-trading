# Service Skeletons

This directory defines the production service boundaries and minimal contracts for implementation.

## Services
- `market-data/` - Alpaca feed adapters, normalization, sequence/watermark enforcement.
- `alpha-engine/` - strategy runtime (virtual-thread I/O + bounded CPU pools).
- `risk/` - deterministic pre-trade checks.
- `oms/` - order state machine + broker adapters.
- `portfolio/` - position/PnL reconciliation.
- `control-plane/` - strategy lifecycle, policy, and orchestration APIs.

## Baseline implementation requirements
1. Every service emits OpenTelemetry traces and Prometheus metrics.
2. Every decision edge writes immutable audit events to `audit.decisions.v1`.
3. Every external call has explicit timeout and retry/circuit-breaker policy.
4. Live-mode consumers use bounded backpressure config from `kafka/topics.yaml`.
