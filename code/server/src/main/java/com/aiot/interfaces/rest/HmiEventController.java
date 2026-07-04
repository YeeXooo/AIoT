package com.aiot.interfaces.rest;

import com.aiot.application.HmiEventStore;
import com.aiot.application.HmiEventStore.HmiEvent;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers/{driverId}/hmi-events")
public class HmiEventController {

    private final HmiEventStore store;

    public HmiEventController(HmiEventStore store) {
        this.store = store;
    }

    @GetMapping("/pending")
    public Map<String, Object> getPending(@PathVariable String driverId) {
        List<HmiEvent> pending = store.findByDriverId(driverId);
        if (pending.isEmpty()) {
            return Map.of("hasPending", false);
        }
        HmiEvent event = pending.get(0);
        return Map.of(
                "hasPending", true,
                "eventId", event.eventId(),
                "eventType", event.eventType(),
                "title", event.title(),
                "description", event.description()
        );
    }

    @PostMapping("/{eventId}/ack")
    public Map<String, Object> acknowledge(
            @PathVariable String driverId,
            @PathVariable String eventId) {
        boolean removed = store.remove(eventId).isPresent();
        return Map.of("eventId", eventId, "acknowledged", removed);
    }
}
