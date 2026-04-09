package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortfolioProjection {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Map<String, Integer> positions = new ConcurrentHashMap<>();

    public PortfolioProjection(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.OMS_EXEC_REPORTS_V1, groupId = "portfolio")
    public void onExecutionReport(MarketEvents.ExecutionReportEvent report) {
        positions.merge(report.orderId(), report.fillQty().intValue(), Integer::sum);
        kafkaTemplate.send(TopicNames.PORTFOLIO_UPDATES_V1, report.orderId(),
                Map.of("eventTime", Instant.now().toString(), "orderId", report.orderId(), "position", positions.get(report.orderId())));
    }
}
