package com.throttlex.persistence;

import com.throttlex.model.UsageRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import javax.persistence.LockModeType;


import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<UsageRecord, Long> {

    /**
     * Fetch usage record with a pessimistic write lock to ensure
     * only one thread updates tokens at a time under high concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UsageRecord> findByKeyId(String keyId);

    /**
     * Atomic decrement — reduces DB round-trips under burst traffic.
     * Returns the number of rows updated (1 if successful, 0 if no tokens left).
     */
    @Modifying
    @Query("UPDATE UsageRecord r SET r.tokens = r.tokens - 1 WHERE r.keyId = :keyId AND r.tokens > 0")
    int decrementToken(@Param("keyId") String keyId);
}
