package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class RiskPolicyEngine {
    private static final BigDecimal MAX_NOTIONAL = BigDecimal.valueOf(250000);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RiskPolicyEngine(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.ALPHA_INTENTS_V1, groupId = "risk")
    public void validate(MarketEvents.PositionIntentEvent intent) {
        String decision = intent.targetNotional().abs().compareTo(MAX_NOTIONAL) <= 0 ? "ALLOW" : "REJECT";
        var event = new MarketEvents.RiskDecisionEvent(intent.strategyId(), intent.symbol(), Instant.now(), decision,
                decision.equals("ALLOW") ? "within hard limits" : "max notional breach");
        kafkaTemplate.send(TopicNames.RISK_DECISIONS_V1, intent.symbol(), event);
    }
}
