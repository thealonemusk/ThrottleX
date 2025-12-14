package com.throttlex.service;

import com.throttlex.limiter.LimiterFactory;
import com.throttlex.persistence.UsageRepository;
import com.throttlex.model.UsageRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
public class ThrottleXService {

    private final UsageRepository repo;
    private final LimiterFactory factory;

    public String extractKey(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    public boolean check(String key) {
        UsageRecord record = repo.findByKeyId(key)
                .orElseGet(() -> {
                    UsageRecord r = new UsageRecord();
                    r.setKeyId(key);
                    r.setTokens(100L);
                    r.setLastRefill(System.currentTimeMillis());
                    return repo.save(r);
                });

        return factory.allow("token-bucket", record, 100, 10);
    }
}
