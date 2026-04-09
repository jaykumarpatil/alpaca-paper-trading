package com.alpaca.hft;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/control")
public class ControlPlaneController {
    @PostMapping("/strategy/start")
    public String start(@RequestParam String strategyId) {
        return "strategy " + strategyId + " start command accepted";
    }

    @PostMapping("/strategy/stop")
    public String stop(@RequestParam String strategyId) {
        return "strategy " + strategyId + " stop command accepted";
    }

    @PostMapping("/kill-switch")
    public String killSwitch() {
        return "global trading halt activated";
    }
}
