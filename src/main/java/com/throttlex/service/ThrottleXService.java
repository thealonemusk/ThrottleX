package com.throttlex.service;

import com.throttlex.limiter.LimiterFactory;
import com.throttlex.model.Policy;
import com.throttlex.model.PolicyEntity;
import com.throttlex.model.UsageRecord;
import com.throttlex.persistence.PolicyRepository;
import com.throttlex.persistence.UsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThrottleXService {

    private final UsageRepository usageRepository;
    private final PolicyRepository policyRepository;
    private final LimiterFactory limiterFactory;
    private final PolicyService policyService;

    /** Extract the throttling key from the incoming request (IP-based). */
    public String extractKey(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }

    /**
     * Check whether the request identified by {@code key} is allowed.
     * Looks up the applicable policy (persisted or default), then delegates
     * to the correct algorithm.
     */
    @Transactional
    public boolean check(String key) {
        // Resolve policy — fall back to a default token-bucket policy if none configured
        Policy policy = policyRepository.findByPolicyKey(key)
                .map(policyService::toPolicy)
                .orElseGet(() -> Policy.builder()
                        .key(key)
                        .type(Policy.PolicyType.TOKEN_BUCKET)
                        .capacity(100)
                        .refillRate(10)
                        .windowSeconds(60)
                        .build());

        // Resolve (or create) the usage record for this key
        UsageRecord record = usageRepository.findByKeyId(key)
                .orElseGet(() -> {
                    UsageRecord r = new UsageRecord();
                    r.setKeyId(key);
                    r.setTokens(policy.getCapacity());
                    r.setLastRefill(System.currentTimeMillis());
                    return usageRepository.save(r);
                });

        boolean allowed = limiterFactory.allow(policy.getType().name(), record, policy);

        // Persist updated token state (relevant for token-bucket)
        usageRepository.save(record);
        return allowed;
    }

    /** Return all usage records (for admin metrics). */
    public List<UsageRecord> getAllUsage() {
        return usageRepository.findAll();
    }

    /** Reset the usage state for a given key. */
    @Transactional
    public void resetKey(String key) {
        usageRepository.findByKeyId(key).ifPresent(r -> {
            Policy defaultPolicy = policyRepository.findByPolicyKey(key)
                    .map(policyService::toPolicy)
                    .orElseGet(() -> Policy.builder().capacity(100).refillRate(10).build());
            r.setTokens(defaultPolicy.getCapacity());
            r.setLastRefill(System.currentTimeMillis());
            usageRepository.save(r);
        });
    }
}
