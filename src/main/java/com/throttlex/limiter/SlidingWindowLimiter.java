package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.SlidingWindowRecord;
import com.throttlex.model.UsageRecord;
import com.throttlex.persistence.SlidingWindowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SlidingWindowLimiter implements Limiter {

    private final SlidingWindowRepository slidingWindowRepository;

    @Override
    @Transactional
    public boolean allow(UsageRecord record, Policy policy) {
        long now = System.currentTimeMillis();
        long windowMs = policy.getWindowSeconds() * 1000L;
        long windowStart = now - windowMs;
        String keyId = record.getKeyId();

        // 1. Remove expired entries outside the current window to keep table lean
        slidingWindowRepository.deleteExpired(keyId, windowStart);

        // 2. Count requests within the current window
        long count = slidingWindowRepository.countRequestsInWindow(keyId, windowStart);

        // 3. Deny if at or over capacity
        if (count >= policy.getCapacity()) {
            return false;
        }

        // 4. Log this request into the window
        slidingWindowRepository.save(
                SlidingWindowRecord.builder()
                        .keyId(keyId)
                        .requestTime(now)
                        .build()
        );
        return true;
    }
}
