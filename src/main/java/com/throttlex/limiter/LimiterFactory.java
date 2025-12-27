package com.throttlex.limiter;

import com.throttlex.model.UsageRecord;
import com.throttlex.model.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting and executing the appropriate rate limiter based on policy type.
 */
@Component
@RequiredArgsConstructor
public class LimiterFactory {

    private final TokenBucketLimiter tokenBucketLimiter;
    private final SlidingWindowLimiter slidingWindowLimiter;

    /**
     * Check if a request is allowed based on the policy type.
     * 
     * @param type The limiter type: "token-bucket" or "sliding-window"
     * @param record The usage record for the key
     * @param capacity Maximum tokens/requests allowed
     * @param refillRateOrWindowSeconds For token-bucket: refill rate per second. 
     *                                   For sliding-window: window size in seconds
     * @return true if allowed, false if rate limited
     */
    public boolean allow(String type, UsageRecord record,
                         long capacity, long refillRateOrWindowSeconds) {

        return switch (type) {
            case "token-bucket" -> {
                Policy policy = Policy.builder()
                        .type(Policy.PolicyType.TOKEN_BUCKET)
                        .capacity(capacity)
                        .refillRate(refillRateOrWindowSeconds)
                        .build();
                yield tokenBucketLimiter.allow(record, policy);
            }
            case "sliding-window" -> {
                Policy policy = Policy.builder()
                        .type(Policy.PolicyType.SLIDING_WINDOW)
                        .capacity(capacity)
                        .windowSeconds(refillRateOrWindowSeconds)
                        .build();
                yield slidingWindowLimiter.allow(record, policy);
            }
            default -> throw new IllegalArgumentException("Unknown or unsupported limiter type: " + type);
        };
    }

    /**
     * Overload that accepts a pre-built Policy object.
     */
    public boolean allow(UsageRecord record, Policy policy) {
        return switch (policy.getType()) {
            case TOKEN_BUCKET -> tokenBucketLimiter.allow(record, policy);
            case SLIDING_WINDOW -> slidingWindowLimiter.allow(record, policy);
        };
    }
}
