#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_BOOTSTRAP_SERVERS:?Set KAFKA_BOOTSTRAP_SERVERS}"

while IFS= read -r topic; do
  kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --create --if-not-exists --topic "$topic" \
    --partitions 12 --replication-factor 3
done <<'TOPICS'
md.instruments.v1
md.quotes.v1
md.trades.v1
md.bars.v1
alpha.signals.v1
alpha.positionIntents.v1
risk.decisions.v1
oms.orders.v1
oms.execReports.v1
portfolio.updates.v1
audit.decisions.v1
TOPICS

echo "Kafka topic bootstrap completed."
