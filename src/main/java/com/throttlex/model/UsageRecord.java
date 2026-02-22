package com.throttlex.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "throttlex_usage",
    indexes = {
        @Index(name = "idx_usage_key_id", columnList = "key_id", unique = true),
        @Index(name = "idx_usage_last_refill", columnList = "last_refill")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    @Column(nullable = false)
    private long tokens;

    @Column(name = "last_refill", nullable = false)
    private long lastRefill; // Epoch millis
}
