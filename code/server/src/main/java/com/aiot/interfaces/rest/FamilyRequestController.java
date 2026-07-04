package com.aiot.interfaces.rest;

import com.aiot.application.PendingFamilyRequestStore;
import com.aiot.application.PendingFamilyRequestStore.FamilyRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers/{driverId}/family-requests")
public class FamilyRequestController {

    private final PendingFamilyRequestStore store;

    public FamilyRequestController(PendingFamilyRequestStore store) {
        this.store = store;
    }

    @GetMapping("/pending")
    public Map<String, Object> getPending(@PathVariable String driverId) {
        List<FamilyRequest> pending = store.findByDriverId(driverId);
        boolean hasRequest = !pending.isEmpty();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("hasPendingRequest", hasRequest);
        if (hasRequest) {
            FamilyRequest req = pending.get(0);
            result.put("requestId", req.requestId());
            result.put("accountId", req.accountId());
            result.put("sessionType", req.sessionType());
        }
        return result;
    }

    @PostMapping("/{requestId}/respond")
    public Map<String, Object> respond(
            @PathVariable String driverId,
            @PathVariable String requestId,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "decline");
        Optional<FamilyRequest> removed = store.remove(requestId);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("requestId", requestId);
        result.put("action", action);
        result.put("resolved", removed.isPresent());
        return result;
    }
}
