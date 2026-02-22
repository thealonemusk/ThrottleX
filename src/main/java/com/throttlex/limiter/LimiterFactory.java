package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LimiterFactory {

    private final TokenBucketLimiter tokenBucketLimiter;
    private final SlidingWindowLimiter slidingWindowLimiter;

    public boolean allow(String type, UsageRecord record, Policy policy) {
        return switch (type.toLowerCase()) {
            case "token-bucket", "token_bucket" -> tokenBucketLimiter.allow(record, policy);
            case "sliding-window", "sliding_window" -> slidingWindowLimiter.allow(record, policy);
            default -> throw new IllegalArgumentException("Unknown limiter type: " + type
                    + ". Supported types: token-bucket, sliding-window");
        };
    }
}
