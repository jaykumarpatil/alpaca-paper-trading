package com.alpaca.hft;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfiguration {
    @Bean(destroyMethod = "close")
    public ExecutorService strategyExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
