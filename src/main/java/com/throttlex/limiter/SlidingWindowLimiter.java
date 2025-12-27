package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import com.throttlex.persistence.UsageBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sliding Window rate limiter using MySQL-backed time-bucketed counters.
 * 
 * Implementation uses 1-second buckets stored in throttlex_buckets table.
 * For each request:
 * 1. Calculate current bucket timestamp (epoch seconds)
 * 2. Increment the current bucket count (INSERT ON DUPLICATE KEY UPDATE)
 * 3. Query sum of counts in the sliding window (last N seconds)
 * 4. Allow if sum <= limit, deny otherwise
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowLimiter implements Limiter {

    private final UsageBucketRepository bucketRepository;

    /**
     * Check if a request should be allowed under sliding window rate limiting.
     * 
     * @param record The usage record (used for keyId extraction, tokens field unused)
     * @param policy The policy with windowSeconds and capacity (max requests per window)
     * @return true if request is allowed, false if rate limited
     */
    @Override
    @Transactional
    public boolean allow(UsageRecord record, Policy policy) {
        String keyId = record.getKeyId();
        long windowSeconds = policy.getWindowSeconds();
        long maxRequests = policy.getCapacity();

        // Current time in epoch seconds
        long nowSeconds = System.currentTimeMillis() / 1000;

        // Calculate window boundaries
        long windowStart = nowSeconds - windowSeconds + 1; // inclusive start

        // Get current count in window BEFORE incrementing
        Long currentCount = bucketRepository.sumCountsInWindow(keyId, windowStart, nowSeconds);
        if (currentCount == null) {
            currentCount = 0L;
        }

        // Check if we're already at or over the limit
        if (currentCount >= maxRequests) {
            return false;
        }

        // Increment the current bucket (atomically via INSERT ON DUPLICATE KEY UPDATE)
        bucketRepository.incrementBucket(keyId, nowSeconds);

        // Optionally clean up old buckets (can be done async in production)
        // bucketRepository.deleteOldBuckets(keyId, windowStart - 1);

        return true;
    }

    /**
     * Get remaining requests allowed in the current window.
     * Useful for response headers.
     */
    public long getRemainingRequests(String keyId, long windowSeconds, long maxRequests) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long windowStart = nowSeconds - windowSeconds + 1;

        Long currentCount = bucketRepository.sumCountsInWindow(keyId, windowStart, nowSeconds);
        if (currentCount == null) {
            currentCount = 0L;
        }

        return Math.max(0, maxRequests - currentCount);
    }
}
