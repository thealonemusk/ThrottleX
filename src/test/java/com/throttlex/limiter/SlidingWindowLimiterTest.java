package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import com.throttlex.persistence.UsageBucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SlidingWindowLimiter.
 * Uses mocked repository to test logic without MySQL dependency.
 */
@ExtendWith(MockitoExtension.class)
class SlidingWindowLimiterTest {

    @Mock
    private UsageBucketRepository bucketRepository;

    @InjectMocks
    private SlidingWindowLimiter limiter;

    private UsageRecord record;
    private Policy policy;

    @BeforeEach
    void setUp() {
        record = UsageRecord.builder()
                .keyId("test-key")
                .tokens(0) // Not used for sliding window
                .lastRefill(0)
                .build();

        policy = Policy.builder()
                .key("test-key")
                .type(Policy.PolicyType.SLIDING_WINDOW)
                .capacity(10) // Max 10 requests per window
                .windowSeconds(60) // 60 second window
                .build();
    }

    @Test
    void testAllow_UnderLimit_ShouldAllow() {
        // Current count is 5, limit is 10 -> should allow
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(5L);

        boolean result = limiter.allow(record, policy);

        assertTrue(result);
        verify(bucketRepository).incrementBucket(eq("test-key"), anyLong());
    }

    @Test
    void testAllow_AtLimit_ShouldDeny() {
        // Current count is 10 (at limit) -> should deny
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(10L);

        boolean result = limiter.allow(record, policy);

        assertFalse(result);
        // Should not increment when denied
        verify(bucketRepository, never()).incrementBucket(anyString(), anyLong());
    }

    @Test
    void testAllow_OverLimit_ShouldDeny() {
        // Current count is 15 (over limit) -> should deny
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(15L);

        boolean result = limiter.allow(record, policy);

        assertFalse(result);
        verify(bucketRepository, never()).incrementBucket(anyString(), anyLong());
    }

    @Test
    void testAllow_NoExistingBuckets_ShouldAllow() {
        // No existing buckets (null/0 count) -> should allow first request
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(0L);

        boolean result = limiter.allow(record, policy);

        assertTrue(result);
        verify(bucketRepository).incrementBucket(eq("test-key"), anyLong());
    }

    @Test
    void testAllow_OneUnderLimit_ShouldAllowLastRequest() {
        // Current count is 9, limit is 10 -> should allow (this will be the 10th)
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(9L);

        boolean result = limiter.allow(record, policy);

        assertTrue(result);
        verify(bucketRepository).incrementBucket(eq("test-key"), anyLong());
    }

    @Test
    void testGetRemainingRequests_ReturnsCorrectValue() {
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(7L);

        long remaining = limiter.getRemainingRequests("test-key", 60, 10);

        assertEquals(3, remaining);
    }

    @Test
    void testGetRemainingRequests_AtLimit_ReturnsZero() {
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(10L);

        long remaining = limiter.getRemainingRequests("test-key", 60, 10);

        assertEquals(0, remaining);
    }

    @Test
    void testGetRemainingRequests_OverLimit_ReturnsZero() {
        when(bucketRepository.sumCountsInWindow(eq("test-key"), anyLong(), anyLong()))
                .thenReturn(15L);

        long remaining = limiter.getRemainingRequests("test-key", 60, 10);

        assertEquals(0, remaining);
    }
}
