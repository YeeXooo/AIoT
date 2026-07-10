package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ActiveRiskSetTest {

    @Test
    void emptyHasNoActiveRisks() {
        ActiveRiskSet set = ActiveRiskSet.empty("trip-1");

        assertEquals("trip-1", set.tripId());
        assertTrue(set.activeRisks().isEmpty());
        assertFalse(set.isActive(AlertType.FATIGUE));
        assertNull(set.levelOf(AlertType.FATIGUE));
    }

    @Test
    void addRegistersRisk() {
        ActiveRiskSet set = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "疲劳");

        assertTrue(set.isActive(AlertType.FATIGUE));
        assertEquals(RiskLevel.L2_WARNING, set.levelOf(AlertType.FATIGUE));
    }

    @Test
    void addIsImmutable() {
        ActiveRiskSet original = ActiveRiskSet.empty("trip-1");
        ActiveRiskSet updated = original.add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL,
                Instant.now(), "严重疲劳");

        assertTrue(original.activeRisks().isEmpty());
        assertTrue(updated.isActive(AlertType.FATIGUE));
    }

    @Test
    void addOverwritesSameType() {
        ActiveRiskSet set = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "疲劳")
                .add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL, Instant.now(), "严重疲劳");

        assertEquals(1, set.activeRisks().size());
        assertEquals(RiskLevel.L3_CRITICAL, set.levelOf(AlertType.FATIGUE));
    }

    @Test
    void removeDeletesRisk() {
        ActiveRiskSet set = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "疲劳")
                .remove(AlertType.FATIGUE);

        assertFalse(set.isActive(AlertType.FATIGUE));
    }

    @Test
    void removeIsImmutable() {
        ActiveRiskSet withRisk = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "疲劳");
        ActiveRiskSet removed = withRisk.remove(AlertType.FATIGUE);

        assertTrue(withRisk.isActive(AlertType.FATIGUE));
        assertFalse(removed.isActive(AlertType.FATIGUE));
    }

    @Test
    void multipleDistinctRisksCoexist() {
        ActiveRiskSet set = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "疲劳")
                .add(AlertType.DISTRACTION, RiskLevel.L3_CRITICAL, Instant.now(), "分心");

        assertEquals(2, set.activeRisks().size());
        assertEquals(RiskLevel.L2_WARNING, set.levelOf(AlertType.FATIGUE));
        assertEquals(RiskLevel.L3_CRITICAL, set.levelOf(AlertType.DISTRACTION));
    }
}
