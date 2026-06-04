package com.example.backend.interfaces.controller;

import com.example.backend.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Result<Map<String, Boolean>> health() {
        return Result.success(Map.of("ok", true));
    }
}
