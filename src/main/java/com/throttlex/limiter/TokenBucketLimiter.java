package com.throttlex.limiter;

import com.throttlex.persistence.UsageRecord;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketLimiter {

    public boolean allow(UsageRecord entity, int capacity, int refillRatePerSec) {
        long now = System.currentTimeMillis();
        long elapsedSec = (now - entity.getLastRefill()) / 1000;

        long newTokens = Math.min(capacity, entity.getTokens() + (elapsedSec * refillRatePerSec));
        entity.setTokens(newTokens);
        entity.setLastRefill(now);

        if (newTokens > 0) {
            entity.setTokens(newTokens - 1);
            return true;
        }
        return false;
    }
}
