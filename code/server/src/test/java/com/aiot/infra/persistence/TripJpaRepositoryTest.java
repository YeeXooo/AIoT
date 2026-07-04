package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("TripJpaRepository CRUD")
class TripJpaRepositoryTest {

    @Autowired private TripJpaRepository repository;
    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;

    private void seedDriverAndVehicle() {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId("dd-01"); d.setName("司机1"); d.setPhone("13000000001");
        driverRepo.save(d);
        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId("vv-01"); v.setLicensePlate("京A12345"); v.setVin("VIN001"); v.setTerminalSn("SN001");
        vehicleRepo.save(v);
    }

    private TripJpaEntity trip(String id, String driverId, String vehicleId,
                                LocalDateTime started, LocalDateTime ended) {
        TripJpaEntity t = new TripJpaEntity();
        t.setTripId(id);
        t.setDriverId(driverId);
        t.setVehicleId(vehicleId);
        t.setStartedAt(started);
        t.setEndedAt(ended);
        t.setHardBrakingCount(0);
        t.setHardAccelerationCount(0);
        return t;
    }

    @Nested @DisplayName("基础 CRUD")
    class Crud {
        @Test void saveAndFindById() {
            seedDriverAndVehicle();
            LocalDateTime now = LocalDateTime.now();
            TripJpaEntity t = trip("t-001", "dd-01", "vv-01", now, null);
            repository.save(t);
            TripJpaEntity found = repository.findById("t-001").orElseThrow();
            assertEquals("dd-01", found.getDriverId());
            assertEquals("vv-01", found.getVehicleId());
            assertEquals(now, found.getStartedAt());
            assertNull(found.getEndedAt());
            assertNotNull(found.getCreatedAt());
        }

        @Test void findActiveTrips() {
            seedDriverAndVehicle();
            LocalDateTime now = LocalDateTime.now();
            repository.save(trip("t-act", "dd-01", "vv-01", now, null));
            repository.save(trip("t-end", "dd-01", "vv-01", now.minusHours(2), now.minusHours(1)));
            List<TripJpaEntity> active = repository.findActiveTrips();
            assertEquals(3, active.size());
            assertTrue(active.stream().anyMatch(t -> "t-act".equals(t.getTripId())));
        }

        @Test void findByDriverId() {
            seedDriverAndVehicle();
            DriverJpaEntity d2 = new DriverJpaEntity();
            d2.setDriverId("dd-02"); d2.setName("司机2"); d2.setPhone("13000000002");
            driverRepo.save(d2);
            LocalDateTime now = LocalDateTime.now();
            repository.save(trip("t-d1", "dd-01", "vv-01", now, null));
            repository.save(trip("t-d2", "dd-02", "vv-01", now, null));
            assertEquals(1, repository.findByDriverId("dd-01").size());
        }

        @Test void endTripSetsEndedAt() {
            seedDriverAndVehicle();
            LocalDateTime now = LocalDateTime.now();
            TripJpaEntity t = trip("t-003", "dd-01", "vv-01", now, null);
            repository.save(t);
            t.setEndedAt(now.plusHours(1));
            t.setScoreValue(85);
            repository.save(t);
            TripJpaEntity updated = repository.findById("t-003").orElseThrow();
            assertEquals(85, updated.getScoreValue());
            assertNotNull(updated.getEndedAt());
        }
    }

    @Nested @DisplayName("约束校验")
    class Constraints {
        @Test void missingDriverIdFails() {
            TripJpaEntity t = new TripJpaEntity();
            t.setTripId("t-bad");
            t.setVehicleId("vv-01");
            t.setStartedAt(LocalDateTime.now());
            assertThrows(DataIntegrityViolationException.class, () -> {
                repository.saveAndFlush(t);
            });
        }
    }
}
