package com.huixue.sandbox.api.controller;

import com.huixue.sandbox.api.dto.ApiResponse;
import com.huixue.sandbox.common.enums.LanguageType;
import com.huixue.sandbox.config.DockerProperties;
import com.huixue.sandbox.infrastructure.pool.ContainerPool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.Map;

@RestController
public class SandboxStatusController {
    private final ContainerPool pool;
    private final DockerProperties properties;
    public SandboxStatusController(ContainerPool pool, DockerProperties properties) {
        this.pool=pool; this.properties=properties;
    }
    @GetMapping("/api/sandbox/status")
    public ApiResponse<Map<String, Object>> status() {
        var heap=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return ApiResponse.success(Map.of(
                "languages", Arrays.stream(LanguageType.values()).map(Enum::name).toList(),
                "containers", pool.getTotalContainers(), "maxContainers", properties.getPool().getMaxTotal(),
                "warmupPerLanguage", properties.getPool().getCoreSize(),
                "heapUsedMb", heap.getUsed()/1024/1024, "heapCommittedMb",heap.getCommitted()/1024/1024));
    }
}
