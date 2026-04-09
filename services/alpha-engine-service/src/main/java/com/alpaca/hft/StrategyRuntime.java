package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutorService;

@Service
public class StrategyRuntime {
    private final ExecutorService strategyExecutor;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StrategyRuntime(ExecutorService strategyExecutor, KafkaTemplate<String, Object> kafkaTemplate) {
        this.strategyExecutor = strategyExecutor;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = TopicNames.MD_QUOTES_V1, groupId = "alpha-engine-live")
    public void onQuote(MarketEvents.QuoteEvent quote) {
        strategyExecutor.submit(() -> {
            var signal = new MarketEvents.SignalEvent("mean-revert-v1", quote.symbol(), Instant.now(), "BUY", BigDecimal.valueOf(0.83));
            kafkaTemplate.send(TopicNames.ALPHA_SIGNALS_V1, quote.symbol(), signal);
        });
    }
}
