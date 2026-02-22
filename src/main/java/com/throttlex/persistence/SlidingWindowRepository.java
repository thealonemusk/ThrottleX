package com.throttlex.persistence;

import com.throttlex.model.SlidingWindowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlidingWindowRepository extends JpaRepository<SlidingWindowRecord, Long> {

    /**
     * Count the number of requests for a key within the current window.
     */
    @Query("SELECT COUNT(r) FROM SlidingWindowRecord r WHERE r.keyId = :keyId AND r.requestTime >= :windowStart")
    long countRequestsInWindow(@Param("keyId") String keyId, @Param("windowStart") long windowStart);

    /**
     * Delete expired entries outside the window to keep the table lean.
     */
    @Modifying
    @Query("DELETE FROM SlidingWindowRecord r WHERE r.keyId = :keyId AND r.requestTime < :windowStart")
    void deleteExpired(@Param("keyId") String keyId, @Param("windowStart") long windowStart);
}
