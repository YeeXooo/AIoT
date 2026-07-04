package com.aiot.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class HmiEventStore {

    public record HmiEvent(
            String eventId,
            String driverId,
            String eventType,
            String title,
            String description,
            Instant createdAt
    ) {
        public static HmiEvent create(String driverId, String eventType, String title, String description) {
            return new HmiEvent(
                    UUID.randomUUID().toString(),
                    driverId,
                    eventType,
                    title,
                    description,
                    Instant.now());
        }
    }

    private final Map<String, HmiEvent> pending = new ConcurrentHashMap<>();

    public HmiEvent push(String driverId, String eventType, String title, String description) {
        HmiEvent event = HmiEvent.create(driverId, eventType, title, description);
        pending.put(event.eventId(), event);
        return event;
    }

    public List<HmiEvent> findByDriverId(String driverId) {
        return pending.values().stream()
                .filter(e -> e.driverId().equals(driverId))
                .toList();
    }

    public Optional<HmiEvent> remove(String eventId) {
        return Optional.ofNullable(pending.remove(eventId));
    }
}
