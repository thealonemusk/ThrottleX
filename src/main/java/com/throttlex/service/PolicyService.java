package com.throttlex.service;

import com.throttlex.dto.PolicyRequest;
import com.throttlex.exception.PolicyNotFoundException;
import com.throttlex.model.Policy;
import com.throttlex.model.PolicyEntity;
import com.throttlex.persistence.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    public List<PolicyEntity> listPolicies() {
        return policyRepository.findAll();
    }

    public PolicyEntity getPolicy(String key) {
        return policyRepository.findByPolicyKey(key)
                .orElseThrow(() -> new PolicyNotFoundException(key));
    }

    @Transactional
    public PolicyEntity createPolicy(PolicyRequest request) {
        if (policyRepository.existsByPolicyKey(request.getKey())) {
            throw new IllegalArgumentException("Policy already exists for key: " + request.getKey()
                    + ". Use PUT to update.");
        }
        PolicyEntity entity = PolicyEntity.builder()
                .policyKey(request.getKey())
                .type(request.getType())
                .capacity(request.getCapacity())
                .refillRate(request.getRefillRate())
                .windowSeconds(request.getWindowSeconds())
                .build();
        return policyRepository.save(entity);
    }

    @Transactional
    public PolicyEntity updatePolicy(String key, PolicyRequest request) {
        PolicyEntity entity = policyRepository.findByPolicyKey(key)
                .orElseThrow(() -> new PolicyNotFoundException(key));
        entity.setType(request.getType());
        entity.setCapacity(request.getCapacity());
        entity.setRefillRate(request.getRefillRate());
        entity.setWindowSeconds(request.getWindowSeconds());
        return policyRepository.save(entity);
    }

    @Transactional
    public void deletePolicy(String key) {
        if (!policyRepository.existsByPolicyKey(key)) {
            throw new PolicyNotFoundException(key);
        }
        policyRepository.deleteByPolicyKey(key);
    }

    /**
     * Converts a persisted PolicyEntity to the domain Policy object used by limiters.
     */
    public Policy toPolicy(PolicyEntity entity) {
        return Policy.builder()
                .key(entity.getPolicyKey())
                .type(entity.getType())
                .capacity(entity.getCapacity())
                .refillRate(entity.getRefillRate())
                .windowSeconds(entity.getWindowSeconds())
                .build();
    }
}
