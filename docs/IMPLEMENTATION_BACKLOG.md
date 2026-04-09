# 90-Day Implementation Backlog

## Days 0-30: Foundation
- [x] Canonical event schema package in `schemas/`.
- [x] Kafka topic and consumer profile standards in `kafka/topics.yaml`.
- [x] Service decomposition documented in `services/README.md`.
- [x] Bootstrap script for baseline Kafka topics.
- [ ] Spring Boot microservice repositories with CI test harness.

## Days 31-60: Scale and Safety
- [ ] Virtual-thread runtime implementation in alpha + adapters.
- [ ] Bounded platform-thread pools for CPU-heavy quant workloads.
- [ ] Deterministic replay engine (fixed clock + seeded RNG + input hash).
- [ ] Kubernetes backtest orchestrator and workers.
- [ ] Canary policy automation with rollback gates.

## Days 61-90: Hardening
- [ ] Chaos test suite (broker outage, Kafka impairment, node disruption).
- [ ] Cross-region DR rehearsal and runbooks.
- [ ] mTLS + workload identity + dynamic secrets enforcement.
- [ ] Compliance retention and immutable replay validation.
- [ ] SLO/error-budget policy and rollback automation.
