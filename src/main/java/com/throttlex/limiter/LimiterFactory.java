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
        String t = type.toLowerCase().replace("_", "-");
        if ("token-bucket".equals(t)) {
            return tokenBucketLimiter.allow(record, policy);
        } else if ("sliding-window".equals(t)) {
            return slidingWindowLimiter.allow(record, policy);
        } else {
            throw new IllegalArgumentException(
                "Unknown limiter type: " + type + ". Supported: token-bucket, sliding-window");
        }
    }
}
