package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.SlidingWindowRecord;
import com.throttlex.model.UsageRecord;
import com.throttlex.persistence.SlidingWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlidingWindowLimiterTest {

    @Mock
    private SlidingWindowRepository slidingWindowRepository;

    @InjectMocks
    private SlidingWindowLimiter limiter;

    private Policy policy;
    private UsageRecord record;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        policy = Policy.builder()
                .key("test-key")
                .type(Policy.PolicyType.SLIDING_WINDOW)
                .capacity(5)
                .windowSeconds(60)
                .build();
        record = UsageRecord.builder()
                .keyId("test-key")
                .tokens(0)
                .lastRefill(System.currentTimeMillis())
                .build();
    }

    @Test
    void testAllow_WhenUnderLimit_ShouldAllow() {
        when(slidingWindowRepository.countRequestsInWindow(anyString(), anyLong())).thenReturn(3L);
        when(slidingWindowRepository.save(any(SlidingWindowRecord.class)))
                .thenReturn(SlidingWindowRecord.builder().keyId("test-key").requestTime(System.currentTimeMillis()).build());

        boolean result = limiter.allow(record, policy);

        assertTrue(result);
        verify(slidingWindowRepository).save(any(SlidingWindowRecord.class));
    }

    @Test
    void testAllow_WhenAtLimit_ShouldDeny() {
        when(slidingWindowRepository.countRequestsInWindow(anyString(), anyLong())).thenReturn(5L);

        boolean result = limiter.allow(record, policy);

        assertFalse(result);
        verify(slidingWindowRepository, never()).save(any(SlidingWindowRecord.class));
    }

    @Test
    void testAllow_ExpiredEntriesAreCleaned() {
        when(slidingWindowRepository.countRequestsInWindow(anyString(), anyLong())).thenReturn(0L);
        when(slidingWindowRepository.save(any(SlidingWindowRecord.class)))
                .thenReturn(SlidingWindowRecord.builder().keyId("test-key").requestTime(System.currentTimeMillis()).build());

        limiter.allow(record, policy);

        verify(slidingWindowRepository).deleteExpired(eq("test-key"), anyLong());
    }
}
