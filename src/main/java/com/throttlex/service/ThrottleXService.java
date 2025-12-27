package com.throttlex.service;

import com.throttlex.limiter.LimiterFactory;
import com.throttlex.persistence.UsageRepository;
import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Core service for ThrottleX rate limiting operations.
 */
@Service
@RequiredArgsConstructor
public class ThrottleXService {

    private final UsageRepository repo;
    private final LimiterFactory factory;

    /**
     * Extract the rate limit key from the request.
     * Default implementation uses client IP address.
     */
    public String extractKey(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    /**
     * Check and consume rate limit for the given key using default token-bucket policy.
     * 
     * @param key The rate limit key (e.g., IP address, user ID)
     * @return true if allowed, false if rate limited
     */
    @Transactional
    public boolean check(String key) {
        UsageRecord record = getOrCreateUsageRecord(key);
        boolean allowed = factory.allow("token-bucket", record, 100, 10);
        
        if (allowed) {
            repo.save(record);
        }
        
        return allowed;
    }

    /**
     * Check and consume rate limit using a specific policy.
     * 
     * @param key The rate limit key
     * @param policy The policy to apply
     * @return true if allowed, false if rate limited
     */
    @Transactional
    public boolean checkWithPolicy(String key, Policy policy) {
        UsageRecord record = getOrCreateUsageRecord(key);
        boolean allowed = factory.allow(record, policy);
        
        // Persist token-bucket state changes
        if (allowed && policy.getType() == Policy.PolicyType.TOKEN_BUCKET) {
            repo.save(record);
        }
        // Note: Sliding window persists its own state in SlidingWindowLimiter
        
        return allowed;
    }

    /**
     * Get or create a usage record for the given key.
     */
    private UsageRecord getOrCreateUsageRecord(String key) {
        return repo.findByKeyId(key)
                .orElseGet(() -> {
                    UsageRecord r = new UsageRecord();
                    r.setKeyId(key);
                    r.setTokens(100L); // Default capacity
                    r.setLastRefill(System.currentTimeMillis());
                    return repo.save(r);
                });
    }

    /**
     * Get the remaining tokens/requests for a given key.
     * Useful for response headers.
     */
    public long getRemainingTokens(String key) {
        return repo.findByKeyId(key)
                .map(UsageRecord::getTokens)
                .orElse(100L);
    }
}
