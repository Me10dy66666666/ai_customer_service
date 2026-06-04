package com.example.backend.interfaces.controller;

import com.example.backend.common.Result;
import com.example.backend.infrastructure.persistence.entity.SlaConfig;
import com.example.backend.infrastructure.persistence.mapper.SlaConfigMapper;
import com.example.backend.application.service.SlaCalculationService;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sla-config")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class SlaConfigController {
    private final SlaConfigMapper slaConfigMapper;
    private final SlaCalculationService slaCalculationService;

    @GetMapping
    public Result<List<SlaConfig>> list() {
        return Result.success(slaConfigMapper.findAllActive());
    }

    @GetMapping("/{id}")
    public Result<SlaConfig> get(@PathVariable Long id) {
        return Result.success(slaConfigMapper.selectById(id));
    }

    @PostMapping
    public Result<SlaConfig> create(@RequestBody SlaConfig config) {
        slaConfigMapper.insert(config);
        slaCalculationService.refreshCache();
        return Result.success(config);
    }

    @PutMapping("/{id}")
    public Result<SlaConfig> update(@PathVariable Long id, @RequestBody SlaConfig config) {
        config.setId(id);
        slaConfigMapper.update(config);
        slaCalculationService.refreshCache();
        return Result.success(config);
    }
}
