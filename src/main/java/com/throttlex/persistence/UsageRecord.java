package com.throttlex.persistence;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "throttlex_usage")
@Data
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String keyId;

    private Long tokens;

    private Long lastRefill;
}
