package com.throttlex.limiter;

import com.throttlex.model.UsageRecord;
import com.throttlex.model.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LimiterFactory {

    private final TokenBucketLimiter tokenBucketLimiter;
    // private final SlidingWindowLimiter slidingWindowLimiter;

    public boolean allow(String type, UsageRecord record,
                         long capacity, long refillRate) {

        return switch (type) {
            case "token-bucket" -> {
                Policy policy = Policy.builder()
                        .type(Policy.PolicyType.TOKEN_BUCKET)
                        .capacity(capacity)
                        .refillRate(refillRate)
                        .build();
                yield tokenBucketLimiter.allow(record, policy);
            }
            // case "sliding-window" -> slidingWindowLimiter.allow(record.getKeyId(), capacity, refillRate);
            default -> throw new IllegalArgumentException("Unknown or unsupported limiter type: " + type);
        };
    }
}
