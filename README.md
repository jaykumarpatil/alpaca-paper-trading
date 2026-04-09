# Production-Grade HFT Trading Platform Roadmap (Java 25 / Spring Boot 4)

## 1) Target Architecture (Service Decomposition)

- **Market Data Ingestion Service (hot path + cold path):** Connects to Alpaca’s Market Data v2 API (via HTTP/WebSocket) for real-time equity (and crypto) ticks, quotes and bars.  It normalizes all raw feeds into canonical events (e.g. `InstrumentEvent`, `TradeEvent`, `QuoteEvent`, `BarEvent`) and enforces sequence integrity and watermarking.  Events are published immutably to Kafka topics (partitioned by symbol/venue) for downstream consumption.  A hot-path in-memory cache (e.g. Redis or Aeron) can hold the latest quote book for ultra-low-latency lookups, while a cold-path writes data to a time-series database (QuestDB) and object storage (Parquet files) for analytics and historical replay【25†L28-L33】【51†L153-L160】.  QuestDB is designed for extreme time-series performance (trading floors, mission-control), with *“ultra-low latency [and] high ingestion throughput”* and native Parquet/SQL support【25†L28-L33】.  For longer-term reference and complex joins (e.g. audit or order history), TimescaleDB (Postgres extension) is used: it provides PostgreSQL-level relational features with hypertables and compression for time-series workloads【28†L183-L191】. 

- **Alpha (Strategy) Engine:** A multi-tenant, low-latency strategy runtime.  Each strategy instance runs in its own *virtual thread* execution context (Java 25 Loom threads) with strict CPU and memory quotas per tenant/strategy.  In **live mode**, each strategy consumes the Kafka market-data stream(s) and generates trading signals (`SignalEvent`) and `PositionIntentEvent`s; in **backtest mode**, a deterministic replay engine feeds historical partitions with a controlled clock.  Results (signals, intents, performance metrics) are emitted as Kafka events.  (Virtual threads allow thousands of concurrent strategies to be run using simple blocking code – see section 3.) 

- **Risk Management Service:** A stateless, deterministic checker that authorizes or rejects orders *before* submission.  It enforces *hard limits* (max order size, price collars to prevent fat-fingers, gross/net position limits, per-symbol exposure caps) and *soft limits* (e.g. intraday drawdown or volatility thresholds).  Consuming candidate orders or signal events, it produces `RiskDecisionEvent` (`ALLOW`/`REJECT`/`THROTTLE`) onto Kafka.  Because risk decisions must be auditable and replayable, the service is side-effect-free and deterministic. 

- **Order Management System (OMS) + Broker Adapter:** Only risk-approved orders enter the OMS.  The OMS tracks each order’s lifecycle (NEW → ACKED → PARTIAL → FILLED/CANCELED/REJECTED) in a persistent state machine, and forwards marketable orders to external brokers.  We integrate first with Alpaca’s Trader API v2 for equity and crypto executions, and design pluggable adapters for multiple brokers to enable failover.  OMS writes all order events and execution reports back into Kafka (`OrderEvent`, `ExecutionReportEvent`).  (Alpaca provides REST endpoints and streaming status updates – see *Alpaca Integration* below.) 

- **Portfolio / PnL / Treasury Service:** Continuously reconciles realized and unrealized PnL by combining the local order/trade intents with broker fills.  Positions are marked using configurable prices (e.g. mid-price or last trade), consistent with risk models.  This service maintains current portfolio state and cash balances and streams updates to Kafka (`PortfolioUpdate`), or exposes it via REST for reporting.

- **Control Plane Services:** Central orchestration and management.  This includes a *Strategy Registry* (versioned strategy artifacts, configuration, feature flags), a *Scheduler/Orchestrator* to launch backtests or deploy new strategy versions, and an *AuthN/AuthZ + Policy Engine* for user and service permissions.  Administration (e.g. strategy start/stop commands, risk overrides) is done via synchronous REST/gRPC calls to control-plane components (see section 2).

## 2) Communication Topology (Kafka + REST/gRPC Hybrid)

We adopt an **event-driven core** with Kafka, paired with a **synchronous control plane** of REST/gRPC:

- **Kafka (async event plane):** All market data, signals, and trade lifecycle events flow through Kafka topics for high-throughput fan-out, replayability and auditing.  Key topics include `md.quotes.v1`, `md.trades.v1`, `md.bars.v1` (market data per symbol), `alpha.signals.v1` (per-strategy signals), `risk.decisions.v1`, `oms.orders.v1` and `oms.execReports.v1` (order events), and `portfolio.updates.v1`.  Partitioning is by symbol (for market data) or by strategy ID (for strategy outputs) to preserve ordering.  Topics should not use compaction for journal-like data (only on snapshot state topics if any).  Consumers use small `max.poll.records` to limit batch size based on processing capacity【15†L350-L358】, and implement backpressure via pausing or flow control if downstream queues fill up.

- **REST/gRPC (sync control plane):** Admin operations and strategy commands use low-latency RPCs.  For example, gRPC endpoints can start/stop strategies, adjust risk parameters, or query state.  This avoids pulling critical commands through Kafka which would introduce latency.  The mix of Kafka (for events) and gRPC/REST (for control) is a common hybrid pattern: Kafka excels at async high-throughput messaging, whereas gRPC/REST give immediate request-response interactions for tight control-plane operations【51†L162-L169】.

## 3) Java 25 Virtual Threads + Spring Boot 4 Concurrency Pattern

- **Blocking I/O on Virtual Threads:** We configure Spring Boot to run blocking I/O (broker REST calls, database queries, HTTP handlers) on **virtual threads**.  For example, a Spring bean can return `Executors.newVirtualThreadPerTaskExecutor()` so that each request or I/O task runs on its own lightweight thread【9†L417-L420】.  Virtual threads (Loom) make such blocking I/O cheap and scalable.  In practice, services like market-data ingestion, broker adapters, and REST controllers will use virtual threads for network calls and DB access.

- **CPU-bound tasks on bounded pools:** Any heavy computation (e.g. risk model math, strategy optimization) should not be done on an unbounded loom pool.  Instead, isolate CPU-intensive work on a fixed-size `ExecutorService` (platform threads) or use structured concurrency with deadlines.  Virtual threads do not speed up pure compute tasks, so we explicitly constrain parallelism for those paths【9†L493-L498】【13†L59-L62】. 

- **Structured Concurrency:** Use Loom’s structured concurrency (JEP 505) to group related tasks.  For example, fetching multiple exchange quotes in parallel with a timeout and cancel-on-failure: 
  ```java
  try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var f1 = scope.fork(() -> venue1.getQuote(symbol));
    var f2 = scope.fork(() -> venue2.getQuote(symbol));
    scope.join();
    scope.throwIfFailed();
    Quote best = selectBest(f1.resultNow(), f2.resultNow());
    return best;
  }
  ```
  Structured scopes allow coordinated cancellation and failure handling【9†L443-L452】. 

- **Rate-limit per strategy:** Even with virtual threads, we guard against overload.  For example, use a `Semaphore` or rate-limiter to cap the number of concurrent operations per strategy or tenant.  As best practice: *“don’t pool virtual threads… instead use rate limiters or semaphores to protect scarce resources”*【9†L487-L495】.  We also enforce timeouts on all external calls, since Loom removes thread scarcity but does not eliminate external latency.

- **Backpressure and Bulkheads:** Prevent strategy fan-out from overwhelming the system.  Tune Kafka’s `max.poll.records` so each strategy’s consumer only fetches a bounded batch【15†L350-L358】.  Use bounded queues (mailboxes) for buffering signals.  Insert circuit-breakers (e.g. Resilience4j) so that repeated slowdowns in downstream (broker or risk) trips fast-fail or throttling.  Finally, segregate strategy tiers (HFT vs mid-frequency vs research) into separate consumer groups or thread pools for resource isolation (bulkhead isolation).

## 4) Massive-Scale Parallel Backtesting Framework

- **Task Decomposition:** A backtest is defined by (strategy version, parameter set, symbol shard, date range).  We partition the Cartesian space into many independent `BacktestTask` messages.  Each task is *idempotent* (deterministic given the same inputs) and represents replaying a slice of history.

- **Kubernetes Execution:** Use an orchestrator (could be a Kubernetes Job or custom operator) to emit these tasks to a queue (Kafka or other queue).  A `backtest-worker` Deployment runs many pods that consume tasks.  Historical tick data is mounted via an object store (e.g. S3) with a local NVMe cache sidecar for hot shards.  The worker uses a deterministic clock and seeded RNG so results are reproducible.  

- **Autoscaling:** Use Kubernetes HPA on the workers (metrics: queue length, task age, CPU).  Cluster Autoscaler maintains separate node pools: a *compute-optimized* pool (preemptible nodes) for backtests, and a *low-latency/critical* pool for live-trading pods.  Assign Kubernetes [PriorityClasses] for pods (`live-trading-critical` > `risk` > `backtest-batch`).  Backtest pods are lower priority (preemptible); live/OMS pods are non-preemptible.  This ensures live trading always has headroom. 

- **Resource Governance:** Create Kubernetes namespaces with quotas per environment (`prod-live`, `prod-sim`, `research`).  Apply **PodDisruptionBudgets** for live services to guarantee minimum replicas during node maintenance【23†L900-L904】.  Taint the compute-heavy backtest nodes (e.g. `hft-workload=backtest:NoSchedule`) so that critical services (OMS, risk) do not land on those nodes.  

## 5) Data Layer & Stateful Reliability

- **Time-Series Storage:** We use **QuestDB** for high-throughput market data ingestion and real-time analytics.  QuestDB advertises *“peak time-series performance”*: it can ingest millions of rows per second and execute vectorized SQL queries with SIMD acceleration【25†L28-L33】.  Its open Apache/Parquet storage format avoids vendor lock-in.  Use QuestDB for raw ticks/quotes/bars and fast ad-hoc queries (e.g. rolling indicators, telemetry). 

  **TimescaleDB (PostgreSQL)** backs relational state: order logs, PnL snapshots, compliance/audit data.  As a Postgres extension, TimescaleDB brings hypertables, chunking and compression to time-series data.  This lets us use familiar SQL and indexing for joins and historical queries while handling large time-series efficiently【28†L183-L191】.  Roughly: QuestDB for blazing-fast market data streams; TimescaleDB for transactional/audit data and aggregated analytics.

- **Kubernetes Stateful Patterns:** Deploy stateful stores (QuestDB, Timescale) as **StatefulSets** with Pod anti-affinity across failure domains.  Use high-IOPS PersistentVolumes and PVCs for storage.  Run multiple replicas (QuestDB supports replication) and use an operator or init scripts for DB clustering.  Ensure WAL segments and periodic snapshots are offloaded to a remote object store: this achieves RPO ≤1 minute for critical stores.  E.g., enable streaming replication with backup on leader.  For DR, replicate databases across regions (with disaster-playbooks tested).

## 6) Brokerage-Grade CI/CD (GitOps + Canary)

- **Pipeline Stages:** Implement a multi-stage pipeline.  (1) *Build* – compile code, run unit tests, perform SAST scans (e.g. Sonar/Snyk) and generate an SBOM (Software Bill of Materials) as required by security best practices.  (2) *Verification* – run deterministic backtests or replay tests against golden historical sessions to verify no behavior changes.  (3) *Simulation Gate* – deploy the new strategy version in a shadow paper-trading mode (shadow mode) to validate logic against live data without affecting real trades.  (4) *Progressive Delivery* – use GitOps (Argo CD/Flux) with progressive rollout (e.g. Argo Rollouts) so traffic ramps: 1% → 5% → 25% → 100% as each SLO is met.  (5) *Post-deploy checks* – monitor key health metrics (e.g. p99 order latency, reject rates) and compare to baseline.

- **Canary & Rollback:** Automate rollback if thresholds trigger.  For example, if risk-rejects spike beyond a threshold, or if order-ACK latency degrades, the canary deployment is aborted.  Provide a **manual kill-switch** (GlobalCircuit API) requiring dual authorization (“four eyes principle”) to halt all trading instantly.

## 7) Observability, Telemetry, and SRE Controls

- **Distributed Tracing (OpenTelemetry):** Instrument every service with OpenTelemetry.  Propagate trace context via Kafka headers and gRPC metadata so we can trace a flow from market data ingest through strategy to OMS.  A typical trace spans `md_ingest` → `alpha_eval` → `risk_check` → `oms_submit` → `broker_ack`.  Tag spans with dimensions like `strategyId`, `symbol`, `venue`, `deploymentVersion` for queryability.  (Context propagation follows W3C tracecontext standards across service boundaries【40†L831-L840】.)

- **Metrics (Prometheus):** Export metrics on each service.  Essential metrics include latency histograms (p99) for each step: e.g. `alpha_eval_latency_ms`, `risk_check_latency_ms`, `oms_submit_to_ack_latency_ms`.  Throughput counters: ticks/sec per symbol, orders/sec per strategy.  System health: Kafka consumer lag per partition/group, count of dropped or NAKed events, reconciliation mismatch count.  Trading-specific: rolling Sharpe, max drawdown, hit ratio for active strategies.  (Following SRE “four golden signals”, we focus on latency, errors, traffic, saturation【43†L129-L138】.)

- **Dashboards & Alerting:** Create Grafana dashboards per team: (a) **Infra/NOC:** Kafka lags, CPU/memory of pods, disk usage. (b) **Trading Safety:** overall order reject rates, total notional-at-risk, circuit-breaker statuses. (c) **Strategy Health:** PnL volatility, Sharpe trends, turnover, drawdown. Set alerts (PagerDuty) by severity: e.g. P1 for trading outages or data feed loss, P2 for performance degradation, P3 for batch/backtest delays.  Link alerts into an on-call rotation.

## 8) Security, Compliance, and Auditability

- **Service Mesh Identity:** Use mTLS for all service-to-service calls.  For example, use SPIFFE/SPIRE to issue short-lived X.509 certs for each pod, or Istio/Envoy’s built-in mTLS.  Indeed’s architecture is illustrative: *“SPIRE-issued x509 identities are used in our Istio mesh for mTLS, and JWT identities are used to enable OIDC-based federated access”*【46†L73-L76】.  We adopt the same zero-trust model: every pod has a cryptographic identity (OIDC via Kubernetes ServiceAccount) and communicates over TLS.

- **Secrets & Workload Identity:** No static secrets in code or pods.  Use Kubernetes ServiceAccount tokens (OIDC) for permissions.  Store secrets (DB passwords, API keys) in Vault or cloud KMS with short TTL dynamic credentials.  As HashiCorp advises, *“Credentials should be short-lived and rotated frequently”* (e.g. Vault dynamic DB credentials)【49†L1-L4】.  Use Pod Identity (e.g. EKS IAM, GKE Workload Identity) to allow pods to fetch secrets securely at runtime.

- **Audit Logs:** All decision points (signals, risk decisions, order submissions, broker responses) are logged immutably (e.g. Kafka or append-only storage) with timestamps and user/context.  This provides an audit trail for compliance.  Apply data retention policies per regulatory class (e.g. keep order/trade events longer than market data).

- **Supply Chain Security:** Build artifacts are signed and versioned.  Enforce policy that only images built from approved repos (with signed attestations) can be deployed (admission controllers).  Maintain provenance in a vulnerability scanner (SBOM) to ensure compliance.

## 9) Reliability Engineering Targets (Example SLOs)

- **OMS-to-broker latency:** P99 end-to-end (submit→ack) < 40 ms (in-region, excluding internet variation).  
- **Risk check latency:** P99 < 5 ms.  
- **Market-data loss:** Primary feed must have 0% data loss (with automated replay recovery on failure).  
- **Kafka E2E lag:** P99 < 100 ms in live mode from producer to consumer (on well-provisioned brokers).  
- **Backtest cluster utilization:** >70% while still meeting live trading SLOs.  

Tie error budgets to these SLOs.  If e.g. OMS latency SLO breaches error budget, trigger automatic rollback.

## 10) Alpaca Integration Notes

Alpaca provides comprehensive API docs and official Postman collections for Market Data v2 and Trader v2【51†L153-L160】【51†L162-L169】.  We leverage these as the source of truth for integration.  For example, Alpaca’s Market Data API offers real-time and historical data via HTTP and WebSocket【51†L153-L160】.  They host a public Postman workspace/GitHub repo of API examples – **use those examples as integration tests**【51†L162-L169】.  Similarly, use Alpaca’s Trading/Broker API documentation to implement order placement and streaming updates (following their authentication and payload format).  These official references ensure our broker adapter correctly handles payloads and auth flows.

## 11) Suggested 90-Day Delivery Plan

- **Days 0–30 (Foundational Setup):** Define event schemas (Avro/Protobuf) and provision Kafka cluster. Stand up base Spring Boot microservices (Market Data ingest, OMS, Risk) with stub logic and full audit logging. Configure OpenTelemetry tracing and a minimal Prometheus/Grafana setup. Build CI pipeline (compile, unit tests, static analysis, SBOM generation).

- **Days 31–60 (Scale & Resilience):** Implement the virtual-thread strategy runtime and tune thread pools. Complete deterministic backtest engine and orchestration (task graph generation, worker pods). Develop continuous deployment pipelines with GitOps and set up a basic canary rollout process (e.g. Argo Rollouts) with automated rollback gates. Begin performance load testing and optimization.

- **Days 61–90 (Production Hardening):** Conduct chaos testing (broker failover, Kafka broker kill, node drains) and validate PodDisruptionBudgets and failover procedures. Perform a full DR rehearsal (region failover). Finalize compliance checklists and runbook, ensure audit logs and SLO alerts are in place. Freeze strategy code for GA release, finalize security scans, and obtain sign-offs.

**Sources:** We relied on the official Alpaca docs and Postman collections【51†L153-L160】【51†L162-L169】 for API details, Java Project Loom references【9†L487-L494】【13†L55-L62】 for concurrency patterns, Kafka best practices【15†L350-L358】, time-series DB documentation【25†L28-L33】【28†L183-L191】, Kubernetes docs on priorities and PDBs【18†L908-L917】【23†L900-L904】, Redis architecture blogs【34†L286-L293】, Vault security guidelines【49†L1-L4】, and industry SRE/observability patterns. All recommendations above are drawn from these sources and established cloud-native and fintech best practices.