package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.VehicleId;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Trip 聚合根")
class TripTest {

    @Nested @DisplayName("工厂方法 start")
    class Start {
        @Test void startsTripWithValidInputs() {
            Trip t = Trip.start(
                    new DriverId("d-01"), new VehicleId("v-01"), LocalDateTime.now());
            assertNotNull(t.tripId());
            assertEquals("d-01", t.driverId().id());
            assertEquals("v-01", t.vehicleId().id());
            assertNotNull(t.startedAt());
            assertFalse(t.isEnded());
            assertEquals(1, t.version());
        }

        @Test void nullDriverIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    Trip.start(null, new VehicleId("v-01"), LocalDateTime.now()));
        }

        @Test void nullVehicleIdThrows() {
            assertThrows(NullPointerException.class, () ->
                    Trip.start(new DriverId("d-01"), null, LocalDateTime.now()));
        }

        @Test void nullStartedAtThrows() {
            assertThrows(NullPointerException.class, () ->
                    Trip.start(new DriverId("d-01"), new VehicleId("v-01"), null));
        }
    }

    @Nested @DisplayName("行程结束")
    class End {
        @Test void endTripSetsEndedAt() {
            LocalDateTime started = LocalDateTime.now().minusHours(1);
            Trip t = Trip.start(new DriverId("d-02"), new VehicleId("v-02"), started);
            LocalDateTime ended = LocalDateTime.now();
            t.end(ended);
            assertTrue(t.isEnded());
            assertEquals(ended, t.endedAt().orElseThrow());
        }

        @Test void endBeforeStartThrows() {
            LocalDateTime started = LocalDateTime.now();
            Trip t = Trip.start(new DriverId("d-03"), new VehicleId("v-03"), started);
            assertThrows(IllegalArgumentException.class, () ->
                    t.end(started.minusMinutes(1)));
        }
    }

    @Nested @DisplayName("生理快照")
    class Snapshots {
        @Test void addSnapshotToActiveTrip() {
            Trip t = Trip.start(new DriverId("d-04"), new VehicleId("v-04"), LocalDateTime.now().minusMinutes(30));
            PhysiologicalSnapshot s = new PhysiologicalSnapshot(
                    java.time.Instant.now(), 75, 98.0, 0.3, null, null, null, null, null);
            t.addPhysiologicalSnapshot(s);
            assertEquals(1, t.physiologicalSnapshots().size());
        }

        @Test void addSnapshotToEndedTripThrows() {
            Trip t = Trip.start(new DriverId("d-05"), new VehicleId("v-05"), LocalDateTime.now().minusHours(2));
            t.end(LocalDateTime.now().minusHours(1));
            assertThrows(IllegalStateException.class, () ->
                    t.addPhysiologicalSnapshot(new PhysiologicalSnapshot(
                            java.time.Instant.now(), 75, 98.0, 0.3, null, null, null, null, null)));
        }
    }

    @Nested @DisplayName("行程评分")
    class ScoreManagement {
        @Test void setScoreOnEndedTrip() {
            Trip t = Trip.start(new DriverId("d-06"), new VehicleId("v-06"), LocalDateTime.now().minusHours(3));
            t.end(LocalDateTime.now().minusHours(2));
            t.updateTripScore(TripScore.of(85));
            assertEquals(85, t.tripScore().orElseThrow().getValue());
        }

        @Test void setScoreOnActiveTripThrows() {
            Trip t = Trip.start(new DriverId("d-07"), new VehicleId("v-07"), LocalDateTime.now());
            assertThrows(IllegalStateException.class, () ->
                    t.updateTripScore(TripScore.of(90)));
        }
    }

    @Nested @DisplayName("reconstitute 重建")
    class Reconstitute {
        @Test void reconstituteEndedTripWithScore() {
            LocalDateTime start = LocalDateTime.now().minusHours(4);
            LocalDateTime end = LocalDateTime.now().minusHours(3);
            Trip t = Trip.reconstitute(
                    new TripId("t-r01"), new DriverId("d-r01"), new VehicleId("v-r01"),
                    start, end, 2, 3, 70, 2,
                    start.minusMinutes(1), end.plusMinutes(1));
            assertEquals("t-r01", t.tripId().id());
            assertEquals(70, t.tripScore().orElseThrow().getValue());
            assertTrue(t.isEnded());
            assertEquals(2, t.version());
        }

        @Test void reconstituteActiveTrip() {
            LocalDateTime start = LocalDateTime.now().minusMinutes(10);
            Trip t = Trip.reconstitute(
                    new TripId("t-r02"), new DriverId("d-r02"), new VehicleId("v-r02"),
                    start, null, 0, 0, null, 1, start, start);
            assertFalse(t.isEnded());
            assertTrue(t.tripScore().isEmpty());
        }
    }
}
