package com.alpaca.hft;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelemetryController {
    private final MeterRegistry meterRegistry;

    public TelemetryController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/api/telemetry/heartbeat")
    public String heartbeat() {
        meterRegistry.counter("telemetry.heartbeat").increment();
        return "ok";
    }
}
