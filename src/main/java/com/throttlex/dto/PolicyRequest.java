package com.throttlex.dto;

import com.throttlex.model.Policy;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRequest {
    private String key;
    private Policy.PolicyType type;
    private long capacity;
    private long refillRate;
    private long windowSeconds;
}
