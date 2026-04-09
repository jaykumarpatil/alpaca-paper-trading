# Production-Grade HFT Trading Platform Roadmap (Java 25 / Spring Boot 4)

## 1) Target Architecture (Service Decomposition)

### Core microservices

1. **Market Data Ingestion Service (low latency path)**
   - Connectors: Alpaca Market Data v2 (plus optional redundant feeds in future).
   - Responsibilities:
     - Normalize ticks/quotes/bars to a canonical schema (`InstrumentEvent`, `QuoteEvent`, `TradeEvent`, `BarEvent`).
     - Enforce sequence integrity and event-time watermarking.
     - Publish immutable events to Kafka topics partitioned by symbol and venue.
   - Data sinks:
     - Hot path cache (Redis/Aeron shared memory optional).
     - Cold path to time-series DB (QuestDB/TimescaleDB) and object store (Parquet).

2. **Alpha Engine (strategy runtime + research execution)**
   - Strategy isolation model:
     - One virtual-thread execution context per strategy instance.
     - Strict CPU/memory quotas per tenant/strategy namespace.
   - Execution modes:
     - Live mode consumes market-data Kafka streams and emits `SignalEvent`.
     - Backtest mode replays historical partitions with deterministic clocks.
   - Publishes:
     - `SignalEvent`, `PositionIntentEvent`, `StrategyMetricEvent`.

3. **Risk Management Service (pre-trade + real-time)**
   - Hard limits (block order): max order notional, fat-finger price collars, max gross/net exposure, per-symbol concentration.
   - Soft limits (degrade/alert): intraday drawdown, rolling volatility spike, model drift.
   - Consumes signals/orders, publishes `RiskDecisionEvent` (`ALLOW`, `REJECT`, `THROTTLE`).
   - Must be side-effect-free and deterministic for audit replay.

4. **Order Management System (OMS + broker adapter)**
   - Accepts only risk-approved orders.
   - Maintains order state machine (`NEW` → `ACK` → `PARTIAL_FILL` → `FILL`/`CANCEL`/`REJECT`).
   - Broker adapters:
     - Alpaca Trader API integration.
     - Pluggable adapters for multi-broker failover.
   - Emits lifecycle events (`OrderEvent`, `ExecutionReportEvent`) to Kafka.

5. **Portfolio, PnL, and Treasury Service**
   - Real-time position/PnL reconciliation with broker fills and local intent log.
   - Risk-consistent marks using mid/last configurable policy.

6. **Control Plane Services**
   - Strategy Registry (versioned strategies + config + feature flags).
   - Scheduler/Orchestrator for backtests and deployment waves.
   - AuthN/AuthZ + Policy engine.

---

## 2) Communication Topology (Kafka + REST/gRPC hybrid)

### Why hybrid
- **Kafka (event plane):** high-throughput asynchronous market/event fan-out, replayability, auditability.
- **gRPC/REST (control plane):** synchronous commands (start/stop strategy, risk override, admin ops), low payload latency.

### Suggested topic design
- `md.quotes.v1`, `md.trades.v1`, `md.bars.v1`
- `alpha.signals.v1`
- `risk.decisions.v1`
- `oms.orders.v1`, `oms.execReports.v1`
- `portfolio.updates.v1`
- `telemetry.strategyMetrics.v1`

Partitioning guidance:
- Primary key = `symbol` for market streams.
- For strategy outputs, key by `strategyId` to preserve per-strategy ordering.
- Use compaction only for state snapshots, not for trade/event journals.

---

## 3) Java 25 Virtual Threads + Spring Boot 4 Concurrency Pattern

### Practical model
- Run **blocking I/O** workloads (broker API calls, DB reads, REST control handlers) on virtual threads.
- Keep **CPU-heavy math** (factor computation, optimization, Monte Carlo) on bounded platform-thread pools.

### Spring Boot 4 implementation notes
- Configure servlet/request handling and async task executors to `Executors.newVirtualThreadPerTaskExecutor()`.
- Use structured concurrency for grouped tasks (e.g., multi-venue quote aggregation with deadline + cancel on first failure).
- Apply per-strategy `Semaphore`/rate-limiter guards to prevent virtual-thread over-subscription.
- Enforce explicit timeouts for all external calls; virtual threads remove thread scarcity, not latency risk.

### Safety controls for massive strategy fan-out
- Backpressure chain:
  1. Kafka consumer `max.poll.records` tuned per strategy class.
  2. Strategy mailbox bounded queues.
  3. Circuit breaker opens on repeated slow broker/risk responses.
- Bulkhead isolation by strategy tier (HFT, mid-frequency, research) using separate consumer groups and resource quotas.

---

## 4) Massive-Scale Parallel Backtesting Framework

### Work decomposition
- Backtest job dimensions:
  - strategy version
  - parameter vector
  - symbol universe shard
  - date shard
- Convert each Cartesian segment into an idempotent `BacktestTask` message.

### Kubernetes execution pattern
- `backtest-orchestrator` creates task graph, writes manifests/results index.
- Stateless `backtest-worker` Deployment consumes tasks from Kafka or queue abstraction.
- Historical data mounted via object storage + local NVMe cache sidecar.
- Deterministic replay engine:
  - event-time clock
  - seeded RNG
  - immutable input snapshot hash

### Horizontal scaling policy
- **HPA for worker Deployments** using custom metrics:
  - queue depth
  - task age (oldest pending)
  - worker CPU saturation
- **Cluster Autoscaler** provisions node pools:
  - compute-optimized pool for backtests
  - low-latency pool for live trading
- Priority classes + preemption:
  - `live-trading-critical` > `risk-critical` > `backtest-batch`
  - Backtests are preemptible; live trading is not.

### Resource governance
- Namespace quotas split by environment (`prod-live`, `prod-sim`, `research`).
- PodDisruptionBudgets for live services.
- Taints/tolerations to keep live OMS/Risk pods off noisy backtest nodes.

---

## 5) Data Layer & Stateful Reliability

### Time-series storage choices
- **QuestDB**: very high ingest throughput and SQL for market telemetry/time-series.
- **TimescaleDB**: relational + hypertables, better for joins/audit + transactional metadata.

Recommended split:
- QuestDB for raw tick/quote/bar ingestion + fast analytics.
- Timescale/PostgreSQL for order/audit/risk snapshots and compliance reporting.

### K8s stateful strategy
- StatefulSets with anti-affinity across zones.
- PersistentVolume with high IOPS classes.
- WAL and snapshot backups to object store (RPO ≤ 1 min for OMS/risk critical stores).
- Cross-region replication for DR with tested failover runbooks.

---

## 6) Brokerage-Grade CI/CD (GitOps + Canary)

### Pipeline stages
1. **Build**: compile, unit tests, SAST, dependency scanning, SBOM generation.
2. **Verification**: deterministic replay tests against golden market sessions.
3. **Simulation gate**: canary strategy version in paper-trading shadow mode.
4. **Progressive delivery** (Argo CD/Flux + Argo Rollouts):
   - 1% strategy traffic
   - 5% after stability SLO
   - 25% then 100%
5. **Post-deploy checks**:
   - p99 order submission latency
   - reject ratio delta
   - Sharpe drift threshold

### Financial kill-switches
- Auto-rollback if any condition trips:
  - risk rejects exceed threshold
  - abnormal slippage spike
  - OMS ACK timeout increase
- Manual global circuit breaker endpoint requiring dual authorization (4-eyes principle).

---

## 7) Observability, Telemetry, and SRE Controls

### OpenTelemetry instrumentation
- Trace context propagated through Kafka headers + gRPC metadata.
- Span model:
  - `md_ingest` → `alpha_eval` → `risk_check` → `oms_submit` → `broker_ack`.
- Attach dimensions: `strategyId`, `symbol`, `venue`, `deploymentVersion`.

### Prometheus metrics (must-have)
- Latency histograms:
  - `alpha_eval_latency_ms`
  - `risk_check_latency_ms`
  - `oms_submit_to_ack_latency_ms`
- Throughput:
  - ticks/sec by symbol partition
  - orders/sec by strategy
- Health:
  - Kafka consumer lag per group/topic partition
  - dropped event count
  - reconciliation mismatch count
- Quant metrics:
  - rolling Sharpe (1d/5d/20d), max drawdown, hit ratio

### Grafana dashboards + alerting
- NOC board: infra saturation + Kafka lag + API errors.
- Trading safety board: reject rate, notional at risk, circuit-breaker state.
- Strategy quality board: Sharpe trend, PnL volatility, turnover.
- Alert routing:
  - P1 (live trading failure) pager + on-call bridge
  - P2 performance degradation
  - P3 research/backtest delays

---

## 8) Security, Compliance, and Auditability

- mTLS service-to-service (SPIRE/Istio or cert-manager managed cert lifecycle).
- OIDC workload identity; no static secrets in pods.
- Secrets from Vault/KMS, short TTL dynamic credentials.
- Immutable audit log for every decision edge (`signal`, `risk decision`, `order submission`, `broker response`).
- Data retention policy by regulatory class (order events retained longest).
- Signed artifacts + admission policies (only attested images deployable).

---

## 9) Reliability Engineering Targets (example SLOs)

- OMS submit-to-broker-ACK p99 < 40 ms (in-region path, excluding internet broker variance).
- Risk decision p99 < 5 ms.
- Market-data ingest loss = 0 tolerated on primary feed path (with replay recovery).
- Kafka end-to-end event delay p99 < 100 ms in live session.
- Backtest cluster utilization > 70% while preserving live-trading SLOs.

Error budgets and rollback policies should be tied to these SLOs.

---

## 10) Alpaca Integration Notes

For implementation acceleration and contract validation, use the official Alpaca Postman collections as source-of-truth examples for endpoint payloads and auth wiring:
- Trader v2 API collection
- Market Data v2 API collection

Treat collection examples as integration tests for broker adapter CI.

---

## 11) Suggested 90-Day Delivery Plan

### Days 0–30 (Foundation)
- Establish event schemas, Kafka cluster, base Spring Boot services.
- Implement OMS + Risk skeleton with full audit log.
- Set up OpenTelemetry + Prometheus/Grafana baseline.

### Days 31–60 (Scale + Safety)
- Introduce virtual-thread strategy runtime and bounded compute pools.
- Build deterministic backtest engine and task orchestrator.
- Implement canary release workflow + automated rollback gates.

### Days 61–90 (Production Hardening)
- Chaos drills (broker outage, Kafka partition loss, node failures).
- DR rehearsal and cross-region failover validation.
- Compliance/audit sign-off and runbook finalization.

