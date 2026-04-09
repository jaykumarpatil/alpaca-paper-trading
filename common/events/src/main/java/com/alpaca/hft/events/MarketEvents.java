package com.alpaca.hft.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class MarketEvents {
    private MarketEvents() {}

    public record QuoteEvent(
            String eventId,
            String symbol,
            Instant eventTime,
            long sequence,
            BigDecimal bidPrice,
            BigDecimal askPrice,
            long bidSize,
            long askSize,
            String source) {}

    public record TradeEvent(
            String eventId,
            String symbol,
            Instant eventTime,
            long sequence,
            BigDecimal price,
            long size,
            String exchange,
            String source) {}

    public record BarEvent(
            String eventId,
            String symbol,
            Instant eventTime,
            String timeframe,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            long volume,
            String source) {}

    public record SignalEvent(String strategyId, String symbol, Instant eventTime, String side, BigDecimal confidence) {}
    public record PositionIntentEvent(String strategyId, String symbol, Instant eventTime, BigDecimal targetNotional) {}
    public record StrategyMetricEvent(String strategyId, Instant eventTime, Map<String, BigDecimal> metrics) {}
    public record RiskDecisionEvent(String strategyId, String symbol, Instant eventTime, String decision, String reason) {}
    public record OrderEvent(String orderId, String strategyId, String symbol, Instant eventTime, String state) {}
    public record ExecutionReportEvent(String orderId, String brokerOrderId, Instant eventTime, String status, BigDecimal fillQty, BigDecimal fillPrice) {}
    public record BacktestTask(String taskId, String strategyId, String symbol, Instant start, Instant end, long seed) {}
}
