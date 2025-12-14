package com.throttlex.limiter;

import com.throttlex.model.Policy;
import com.throttlex.model.UsageRecord;

public interface Limiter {
    /**
     * Checks if the request is allowed and consumes tokens/quota if so.
     * @param record The usage record (state) for the key.
     * @param policy The policy definition (limits).
     * @return true if allowed, false if denied.
     */
    boolean allow(UsageRecord record, Policy policy);
}
