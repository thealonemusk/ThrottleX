package com.throttlex.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for UsageBucket entity.
 * Combines keyId (the rate limit key) and bucketTs (epoch seconds).
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageBucketId implements Serializable {

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "bucket_ts", nullable = false)
    private long bucketTs; // Epoch seconds
}
