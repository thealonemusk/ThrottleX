package com.throttlex.persistence;

import com.throttlex.model.PolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PolicyRepository extends JpaRepository<PolicyEntity, Long> {

    Optional<PolicyEntity> findByPolicyKey(String policyKey);

    boolean existsByPolicyKey(String policyKey);

    void deleteByPolicyKey(String policyKey);
}
