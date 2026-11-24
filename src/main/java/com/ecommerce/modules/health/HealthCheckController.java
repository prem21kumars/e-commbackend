package com.ecommerce.modules.health;

import com.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health and diagnostics endpoint for load balancer probes and monitoring.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Health & Diagnostics", description = "Endpoints for platform health probes and system metrics")
public class HealthCheckController {

    private final Environment environment;

    @GetMapping("/ping")
    @Operation(summary = "Ping probe", description = "Fast low-overhead ping verification")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.success("pong"));
    }

    @GetMapping("/health")
    @Operation(summary = "System Health and Environment Diagnostic", description = "Detailed health and memory metric report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "UP");
        details.put("timestamp", Instant.now());
        details.put("activeProfiles", environment.getActiveProfiles());
        details.put("uptimeMs", runtimeBean.getUptime());
        details.put("heapMemoryUsedBytes", memoryBean.getHeapMemoryUsage().getUsed());
        details.put("heapMemoryMaxBytes", memoryBean.getHeapMemoryUsage().getMax());
        details.put("nonHeapMemoryUsedBytes", memoryBean.getNonHeapMemoryUsage().getUsed());

        return ResponseEntity.ok(ApiResponse.success("Platform operating normally", details));
    }
}

