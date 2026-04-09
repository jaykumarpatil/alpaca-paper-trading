package com.alpaca.hft;

import com.alpaca.hft.events.MarketEvents;
import com.alpaca.hft.kafka.TopicNames;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class BacktestWorker {
    @KafkaListener(topics = TopicNames.BACKTEST_TASKS_V1, groupId = "backtest-worker")
    public void runTask(MarketEvents.BacktestTask task) {
        Random random = new Random(task.seed());
        for (int i = 0; i < 1_000; i++) {
            random.nextDouble();
        }
    }
}
