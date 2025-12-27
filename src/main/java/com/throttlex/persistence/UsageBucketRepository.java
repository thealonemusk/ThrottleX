package com.throttlex.persistence;

import com.throttlex.model.UsageBucket;
import com.throttlex.model.UsageBucketId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing sliding window time-bucketed counters.
 */
@Repository
public interface UsageBucketRepository extends JpaRepository<UsageBucket, UsageBucketId> {

    /**
     * Find a bucket by key and timestamp.
     */
    Optional<UsageBucket> findById(UsageBucketId id);

    /**
     * Increment the count for a bucket. If the bucket doesn't exist, create it with count=1.
     * Uses native query for INSERT ... ON DUPLICATE KEY UPDATE pattern.
     */
    @Modifying
    @Query(value = "INSERT INTO throttlex_buckets (key_id, bucket_ts, count) VALUES (:keyId, :bucketTs, 1) " +
            "ON DUPLICATE KEY UPDATE count = count + 1", nativeQuery = true)
    void incrementBucket(@Param("keyId") String keyId, @Param("bucketTs") long bucketTs);

    /**
     * Get the sum of request counts within the specified time window.
     * @param keyId The rate limit key
     * @param fromTs Start of window (epoch seconds, inclusive)
     * @param toTs End of window (epoch seconds, inclusive)
     * @return Sum of counts, or null if no buckets exist
     */
    @Query(value = "SELECT COALESCE(SUM(b.count), 0) FROM throttlex_buckets b " +
            "WHERE b.key_id = :keyId AND b.bucket_ts >= :fromTs AND b.bucket_ts <= :toTs", 
            nativeQuery = true)
    Long sumCountsInWindow(@Param("keyId") String keyId, 
                           @Param("fromTs") long fromTs, 
                           @Param("toTs") long toTs);

    /**
     * Delete old buckets that are outside the sliding window.
     * @param keyId The rate limit key
     * @param beforeTs Delete buckets older than this timestamp (epoch seconds)
     */
    @Modifying
    @Query(value = "DELETE FROM throttlex_buckets WHERE key_id = :keyId AND bucket_ts < :beforeTs", 
            nativeQuery = true)
    void deleteOldBuckets(@Param("keyId") String keyId, @Param("beforeTs") long beforeTs);
}
