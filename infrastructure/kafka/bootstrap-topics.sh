#!/usr/bin/env bash
set -euo pipefail
BS=${1:-localhost:9092}
for t in md.quotes.v1 md.trades.v1 md.bars.v1 alpha.signals.v1 alpha.position-intents.v1 risk.decisions.v1 oms.orders.v1 oms.exec-reports.v1 portfolio.updates.v1 backtest.tasks.v1; do
  kafka-topics.sh --bootstrap-server "$BS" --create --if-not-exists --topic "$t" --partitions 32 --replication-factor 1
done
