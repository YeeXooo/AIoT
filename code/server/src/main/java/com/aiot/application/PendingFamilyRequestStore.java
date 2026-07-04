package com.aiot.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class PendingFamilyRequestStore {

    public record FamilyRequest(
            String requestId,
            String driverId,
            String accountId,
            String sessionType,
            Instant createdAt
    ) {
        public static FamilyRequest create(String driverId, String accountId, String sessionType) {
            return new FamilyRequest(
                    UUID.randomUUID().toString(),
                    driverId,
                    accountId,
                    sessionType != null ? sessionType : "AUDIO",
                    Instant.now());
        }
    }

    private final Map<String, FamilyRequest> pending = new ConcurrentHashMap<>();

    public FamilyRequest put(FamilyRequest request) {
        pending.put(request.requestId(), request);
        return request;
    }

    public Optional<FamilyRequest> get(String requestId) {
        return Optional.ofNullable(pending.get(requestId));
    }

    public List<FamilyRequest> findByDriverId(String driverId) {
        return pending.values().stream()
                .filter(r -> r.driverId().equals(driverId))
                .toList();
    }

    public Optional<FamilyRequest> remove(String requestId) {
        return Optional.ofNullable(pending.remove(requestId));
    }
}
