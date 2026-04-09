package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/backtests")
public class BacktestTaskPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BacktestTaskPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/launch")
    public String launch() {
        var task = new MarketEvents.BacktestTask(UUID.randomUUID().toString(), "mean-revert-v1", "AAPL",
                Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-06-30T00:00:00Z"), 42L);
        kafkaTemplate.send(TopicNames.BACKTEST_TASKS_V1, task.symbol(), task);
        return task.taskId();
    }
}
