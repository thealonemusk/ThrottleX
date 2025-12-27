package com.throttlex.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing sliding window time-bucketed counters.
 * Each bucket represents request counts for a specific key within a 1-second window.
 * Used by SlidingWindowLimiter for MySQL-based rate limiting.
 */
@Entity
@Table(name = "throttlex_buckets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageBucket {

    @EmbeddedId
    private UsageBucketId id;

    @Column(nullable = false)
    private int count;

    /**
     * Convenience constructor for creating a new bucket.
     */
    public UsageBucket(String keyId, long bucketTs, int count) {
        this.id = new UsageBucketId(keyId, bucketTs);
        this.count = count;
    }

    public String getKeyId() {
        return id != null ? id.getKeyId() : null;
    }

    public long getBucketTs() {
        return id != null ? id.getBucketTs() : 0;
    }
}
