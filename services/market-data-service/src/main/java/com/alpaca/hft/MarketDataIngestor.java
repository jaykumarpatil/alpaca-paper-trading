package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MarketDataIngestor {
    private final AtomicLong sequence = new AtomicLong();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MarketDataIngestor(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void connectAndPublish(String symbol) {
        new ReactorNettyWebSocketClient().execute(URI.create("wss://stream.data.alpaca.markets/v2/sip"), session -> {
            // auth/subscribe omitted from snippet; publish uses real Kafka producer.
            var quote = new MarketEvents.QuoteEvent(
                    UUID.randomUUID().toString(), symbol, Instant.now(), sequence.incrementAndGet(),
                    BigDecimal.valueOf(100.01), BigDecimal.valueOf(100.03), 10, 12, "alpaca");
            kafkaTemplate.send(TopicNames.MD_QUOTES_V1, symbol, quote);
            return session.close();
        }).retry(10).block();
    }
}
