package com.alpaca.hft;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/strategies")
public class StrategyRegistryController {
    private final ConcurrentHashMap<String, Map<String, Object>> registry = new ConcurrentHashMap<>();

    @PostMapping("/{strategyId}/{version}")
    public void upsert(@PathVariable String strategyId, @PathVariable String version, @RequestBody Map<String, Object> config) {
        registry.put(strategyId + ":" + version, config);
    }

    @GetMapping("/{strategyId}/{version}")
    public Map<String, Object> get(@PathVariable String strategyId, @PathVariable String version) {
        return registry.get(strategyId + ":" + version);
    }
}
