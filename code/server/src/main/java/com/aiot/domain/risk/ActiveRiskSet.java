package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record ActiveRiskSet(String tripId, Map<AlertType, RiskEntry> activeRisks) {

    public record RiskEntry(RiskLevel level, Instant detectedAt, String summary) { }

    public static ActiveRiskSet empty(String tripId) {
        return new ActiveRiskSet(tripId, Collections.emptyMap());
    }

    public boolean isActive(AlertType type) {
        return activeRisks.containsKey(type);
    }

    public RiskLevel levelOf(AlertType type) {
        RiskEntry entry = activeRisks.get(type);
        return entry != null ? entry.level() : null;
    }

    public ActiveRiskSet add(AlertType type, RiskLevel level, Instant detectedAt, String summary) {
        Map<AlertType, RiskEntry> newMap = new HashMap<>(activeRisks);
        newMap.put(type, new RiskEntry(level, detectedAt, summary));
        return new ActiveRiskSet(tripId, Collections.unmodifiableMap(newMap));
    }

    public ActiveRiskSet remove(AlertType type) {
        Map<AlertType, RiskEntry> newMap = new HashMap<>(activeRisks);
        newMap.remove(type);
        return new ActiveRiskSet(tripId, Collections.unmodifiableMap(newMap));
    }
}
