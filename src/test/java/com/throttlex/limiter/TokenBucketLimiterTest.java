package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketLimiterTest {

    private final TokenBucketLimiter limiter = new TokenBucketLimiter();

    @Test
    void testAllow_BasicRefill() throws InterruptedException {
        // Capacity 10, Refill 1/sec
        Policy policy = Policy.builder()
                .key("test")
                .type(Policy.PolicyType.TOKEN_BUCKET)
                .capacity(10)
                .refillRate(1)
                .build();

        // Initial state: 5 tokens, last refill now
        UsageRecord record = UsageRecord.builder()
                .keyId("test")
                .tokens(5)
                .lastRefill(System.currentTimeMillis())
                .build();

        // 1. Consume 1 token -> should allow
        boolean allowed = limiter.allow(record, policy);
        assertTrue(allowed);
        assertEquals(4, record.getTokens());

        // 2. Consume remaining 4
        limiter.allow(record, policy);
        limiter.allow(record, policy);
        limiter.allow(record, policy);
        limiter.allow(record, policy); // 0 tokens left

        assertEquals(0, record.getTokens());

        // 3. Next one should fail
        assertFalse(limiter.allow(record, policy));

        // 4. Wait 1.1 second -> should refill 1 token
        Thread.sleep(1100);
        
        // 5. Try again -> should allow
        assertTrue(limiter.allow(record, policy));
        // Should have 0 left after consuming the refilled 1
        assertEquals(0, record.getTokens());
    }
}
