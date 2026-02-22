package com.throttlex.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {
    private String key;
    private String algorithm;
    private long currentTokens;
    private long capacity;
    private long windowRequestCount;
    private long windowSeconds;
    private String status; // "OK" or "THROTTLED"
}
