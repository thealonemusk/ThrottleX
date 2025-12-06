package com.throttlex.persistence;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface UsageRepository extends JpaRepository<UsageRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UsageRecord> findByKeyId(String keyId);
}
