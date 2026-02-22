package com.throttlex.controller;

import com.throttlex.dto.MetricsResponse;
import com.throttlex.dto.PolicyRequest;
import com.throttlex.model.PolicyEntity;
import com.throttlex.model.UsageRecord;
import com.throttlex.service.PolicyService;
import com.throttlex.service.ThrottleXService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ThrottleXService throttleXService;
    private final PolicyService policyService;

    // ─── Health & Status ────────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "service", "ThrottleX",
                "status", "UP",
                "version", "1.0.0",
                "timestamp", Instant.now().toString()
        ));
    }

    // ─── Metrics ────────────────────────────────────────────────────────────────

    @GetMapping("/metrics")
    public ResponseEntity<List<MetricsResponse>> getAllMetrics() {
        List<UsageRecord> records = throttleXService.getAllUsage();
        List<MetricsResponse> metrics = records.stream()
                .map(this::toMetrics)
                .collect(Collectors.toList());
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics/{key}")
    public ResponseEntity<MetricsResponse> getMetrics(@PathVariable String key) {
        // getAllUsage filters by key
        List<UsageRecord> all = throttleXService.getAllUsage();
        return all.stream()
                .filter(r -> r.getKeyId().equals(key))
                .findFirst()
                .map(r -> ResponseEntity.ok(toMetrics(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reset/{key}")
    public ResponseEntity<Map<String, String>> resetKey(@PathVariable String key) {
        throttleXService.resetKey(key);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "message", "Rate limit counters reset successfully",
                "timestamp", Instant.now().toString()
        ));
    }

    // ─── Policy CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<List<PolicyEntity>> listPolicies() {
        return ResponseEntity.ok(policyService.listPolicies());
    }

    @GetMapping("/policies/{key}")
    public ResponseEntity<PolicyEntity> getPolicy(@PathVariable String key) {
        return ResponseEntity.ok(policyService.getPolicy(key));
    }

    @PostMapping("/policies")
    public ResponseEntity<PolicyEntity> createPolicy(@RequestBody PolicyRequest request) {
        PolicyEntity created = policyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/policies/{key}")
    public ResponseEntity<PolicyEntity> updatePolicy(@PathVariable String key,
                                                     @RequestBody PolicyRequest request) {
        return ResponseEntity.ok(policyService.updatePolicy(key, request));
    }

    @DeleteMapping("/policies/{key}")
    public ResponseEntity<Map<String, String>> deletePolicy(@PathVariable String key) {
        policyService.deletePolicy(key);
        return ResponseEntity.ok(Map.of(
                "key", key,
                "message", "Policy deleted successfully"
        ));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private MetricsResponse toMetrics(UsageRecord r) {
        PolicyEntity policy = policyService.listPolicies().stream()
                .filter(p -> p.getPolicyKey().equals(r.getKeyId()))
                .findFirst()
                .orElse(null);

        String algorithm = (policy != null) ? policy.getType().name() : "TOKEN_BUCKET";
        long capacity    = (policy != null) ? policy.getCapacity() : 100L;
        long windowSecs  = (policy != null) ? policy.getWindowSeconds() : 60L;

        return MetricsResponse.builder()
                .key(r.getKeyId())
                .algorithm(algorithm)
                .currentTokens(r.getTokens())
                .capacity(capacity)
                .windowSeconds(windowSecs)
                .windowRequestCount(capacity - r.getTokens())
                .status(r.getTokens() > 0 ? "OK" : "THROTTLED")
                .build();
    }
}
