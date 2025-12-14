package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketLimiter implements Limiter {

    @Override
    public boolean allow(UsageRecord record, Policy policy) {
        long now = System.currentTimeMillis();
        long lastRefill = record.getLastRefill();
        long capacity = policy.getCapacity();
        long refillRate = policy.getRefillRate();
        
        // 1. Calculate tokens to add
        // Time elapsed in seconds
        long elapsedMillis = now - lastRefill;
        // Avoid refill if barely any time passed to prevent partial token precision issues
        // (Though double math or staying in millis/micros would be better for high precision)
        // For simplicity: (elapsed / 1000.0) * rate
        
        long tokensToAdd = (long) (elapsedMillis / 1000.0 * refillRate);
        
        // 2. Refill
        long currentTokens = record.getTokens();
        if (tokensToAdd > 0) {
            currentTokens = Math.min(capacity, currentTokens + tokensToAdd);
            record.setLastRefill(now); // Update refill time only if we added tokens? 
            // Better: update refill time based on how many "ticks" passed to avoid time drift?
            // Simple approach: Set to now.
            // Drift-safe approach: lastRefill += tokensToAdd / rate * 1000
            
            // Let's stick to simple "set to now" for Phase 1, but be aware of drift.
            record.setLastRefill(now);
        }

        // 3. Consume
        if (currentTokens >= 1) {
            currentTokens--;
            record.setTokens(currentTokens);
            return true;
        }

        return false;
    }
}
