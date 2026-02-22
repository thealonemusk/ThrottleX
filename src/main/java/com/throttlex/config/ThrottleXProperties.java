package com.throttlex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "throttlex")
@Data
public class ThrottleXProperties {
    /** Default token-bucket capacity (used when no policy is configured for a key). */
    private long defaultCapacity = 100;

    /** Default token refill rate in tokens/second. */
    private long defaultRefillRate = 10;

    /** Default sliding-window size in seconds. */
    private long defaultWindowSeconds = 60;
}
