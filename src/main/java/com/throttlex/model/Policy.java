package com.throttlex.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {
    private String key;
    private PolicyType type;
    private long capacity;
    private long refillRate; // tokens per second
    private long windowSeconds; // for sliding window

    public enum PolicyType {
        TOKEN_BUCKET,
        SLIDING_WINDOW
    }
}
