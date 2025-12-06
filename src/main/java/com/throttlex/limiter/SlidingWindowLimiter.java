package com.throttlex.limiter;

import org.springframework.stereotype.Component;

@Component
public class SlidingWindowLimiter {
    public boolean allow(String key, int windowSize, int maxRequests) {
        // Implement sliding window logic here (placeholder)
        return true;
    }
}
