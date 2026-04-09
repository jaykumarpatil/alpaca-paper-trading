# Task: Build Production-Grade HFT Trading Platform (Java 25 / Spring Boot 4)

## Task Type
Epic / Program-level implementation task

## Objective
Execute a 90-day delivery program to evolve this project into a production-grade, event-driven HFT platform with deterministic risk controls, broker-grade OMS behavior, scalable backtesting, and audited operational safety.

## Scope
- Service decomposition into market data, alpha, risk, OMS, portfolio/PnL, and control-plane services.
- Kafka event-plane + REST/gRPC control-plane communication model.
- Java 25 virtual-thread runtime model with bounded CPU pools.
- Massive-scale parallel backtesting on Kubernetes.
- Stateful storage strategy for time-series + audit/compliance stores.
- GitOps canary deployment and financial kill-switch controls.
- Full observability, security, compliance, DR, and SLO enforcement.

## Deliverables

### D0–D30: Foundation
- [ ] Canonical event schema package (`InstrumentEvent`, `QuoteEvent`, `TradeEvent`, `BarEvent`, plus signal/risk/order events).
- [ ] Kafka topic bootstrap and partitioning standards.
- [ ] Initial microservice skeletons (Market Data, Alpha, Risk, OMS, Portfolio, Control Plane).
- [ ] Deterministic audit log pipeline for all decision edges.
- [ ] Baseline observability (OpenTelemetry tracing + Prometheus + Grafana starter dashboards).

### D31–D60: Scale + Safety
- [ ] Virtual-thread strategy runtime with per-strategy isolation controls.
- [ ] Bounded compute pools for CPU-heavy quant workloads.
- [ ] Deterministic replay/backtest engine with seeded RNG and immutable input hashes.
- [ ] Backtest orchestrator and worker task-queue execution on Kubernetes.
- [ ] Progressive delivery (1% → 5% → 25% → 100%) with automatic rollback guards.

### D61–D90: Production Hardening
- [ ] Chaos drill suite (broker outage, Kafka partition impairment, node failures).
- [ ] Cross-region disaster recovery rehearsal and validated failover runbooks.
- [ ] Security hardening (mTLS, OIDC workload identity, Vault/KMS-backed dynamic secrets).
- [ ] Compliance package (retention, audit replay, signed artifacts, admission policy checks).
- [ ] SLO/error-budget enforcement with rollback policy automation.

## Acceptance Criteria
1. OMS submit-to-broker-ACK p99 under 40 ms (in-region path, excluding external broker internet variance).
2. Risk decision p99 under 5 ms.
3. Kafka end-to-end event delay p99 under 100 ms during live sessions.
4. Deterministic replay reproduces risk and order decisions for a fixed snapshot + seed.
5. Full decision chain (`signal` → `risk` → `order` → `broker response`) is queryable from immutable audit logs.
6. Canary and kill-switch controls automatically halt or roll back unsafe releases.

## Dependencies
- Alpaca Trader v2 and Market Data v2 integration contracts (use official Postman collections as executable contract references).
- Kubernetes cluster with isolated node pools for live-trading and backtest workloads.
- Kafka, Redis (optional hot path), QuestDB/Timescale/PostgreSQL, and object storage foundations.

## Risks
- Latency regressions from unbounded strategy fan-out.
- State divergence between broker fills and internal ledgers.
- Backtest/live behavioral mismatch without deterministic clocks and seeded randomness.
- Operational risk from insufficient kill-switch coverage.

## Out of Scope (for this task)
- Multi-broker smart order routing optimizations beyond basic adapter pluggability.
- Advanced portfolio optimization models not required for core reliability and safety milestones.
