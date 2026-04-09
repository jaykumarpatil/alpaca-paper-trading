package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderLifecycleManager {
    private final RestClient alpacaClient = RestClient.builder().baseUrl("https://paper-api.alpaca.markets").build();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderLifecycleManager(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.RISK_DECISIONS_V1, groupId = "oms")
    public void route(MarketEvents.RiskDecisionEvent decision) {
        if (!"ALLOW".equals(decision.decision())) return;
        String orderId = UUID.randomUUID().toString();
        alpacaClient.post().uri("/v2/orders")
                .body(Map.of("symbol", decision.symbol(), "qty", "1", "side", "buy", "type", "market", "time_in_force", "day"))
                .retrieve().toBodilessEntity();

        kafkaTemplate.send(TopicNames.OMS_ORDERS_V1, decision.symbol(),
                new MarketEvents.OrderEvent(orderId, decision.strategyId(), decision.symbol(), Instant.now(), "ACK"));
        kafkaTemplate.send(TopicNames.OMS_EXEC_REPORTS_V1, decision.symbol(),
                new MarketEvents.ExecutionReportEvent(orderId, "alpaca-" + orderId, Instant.now(), "FILL", BigDecimal.ONE, BigDecimal.TEN));
    }
}
