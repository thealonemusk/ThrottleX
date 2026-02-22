package com.throttlex.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "throttlex_policy",
    indexes = {
        @Index(name = "idx_policy_key", columnList = "policy_key", unique = true)
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_key", nullable = false, unique = true)
    private String policyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Policy.PolicyType type;

    @Column(nullable = false)
    private long capacity;

    @Column(name = "refill_rate", nullable = false)
    private long refillRate; // tokens per second (token-bucket)

    @Column(name = "window_seconds", nullable = false)
    private long windowSeconds; // sliding window size in seconds
}
