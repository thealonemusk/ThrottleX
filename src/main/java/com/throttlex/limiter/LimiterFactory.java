package com.throttlex.limiter;

import com.throttlex.persistence.UsageRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LimiterFactory {

    private final TokenBucketLimiter tokenBucketLimiter;
    private final SlidingWindowLimiter slidingWindowLimiter;

    public boolean allow(String type, UsageRecord record,
                         int capacity, int refillRate) {

        return switch (type) {
            case "token-bucket" -> tokenBucketLimiter.allow(record, capacity, refillRate);
            case "sliding-window" -> slidingWindowLimiter.allow(record.getKeyId(), capacity, refillRate);
            default -> throw new IllegalArgumentException("Unknown limiter type: " + type);
        };
    }
}
