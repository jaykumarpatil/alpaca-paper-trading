# Services

Each subdirectory under `services/` is an independent Spring Boot 4 application.

## Service matrix

- market-data-service: Alpaca market data ingestion, normalization, Kafka publish
- alpha-engine-service: strategy runtime (virtual-thread executor)
- risk-service: deterministic risk policy evaluation
- order-management-service: order state and Alpaca execution adapter
- portfolio-service: position/PnL/exposure projection
- backtest-orchestrator: shard generation and task publish
- backtest-worker: deterministic replay worker
- strategy-registry-service: version/config storage API
- control-plane-service: orchestration API and kill switch
- telemetry-service: observability endpoint(s)
- auth-service: JWT/OIDC RBAC guardrails
