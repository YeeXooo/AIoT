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
@DisplayName("AlertEventJpaRepository CRUD")
class AlertEventJpaRepositoryTest {

    @Autowired private AlertEventJpaRepository repository;
    @Autowired private TripJpaRepository tripRepo;
    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;

    private void seed() {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId("d-alert"); d.setName("驾驶员"); d.setPhone("13100000001");
        driverRepo.save(d);
        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId("v-alert"); v.setLicensePlate("京B11111"); v.setVin("VINALERT"); v.setTerminalSn("SNALERT");
        vehicleRepo.save(v);
        TripJpaEntity t = new TripJpaEntity();
        t.setTripId("t-alert"); t.setDriverId("d-alert"); t.setVehicleId("v-alert");
        t.setStartedAt(LocalDateTime.now().minusHours(1));
        tripRepo.save(t);
    }

    @Nested @DisplayName("基础 CRUD")
    class Crud {
        @Test void saveAndFindById() {
            seed();
            AlertEventJpaEntity a = alert("a-001", "FATIGUE", "L3_CRITICAL");
            repository.save(a);
            AlertEventJpaEntity found = repository.findById("a-001").orElseThrow();
            assertEquals("FATIGUE", found.getAlertType());
            assertEquals("L3_CRITICAL", found.getRiskLevel());
            assertEquals("t-alert", found.getTripId());
            assertNotNull(found.getCreatedAt());
        }

        @Test void findByRiskLevel() {
            seed();
            repository.save(alert("a-L3", "FATIGUE", "L3_CRITICAL"));
            repository.save(alert("a-L2", "DISTRACTION", "L2_WARNING"));
            assertEquals(1, repository.findByRiskLevel("L3_CRITICAL").size());
        }

        @Test void findByDriverId() {
            seed();
            repository.save(alert("a-d1", "FATIGUE", "L2_WARNING"));
            assertEquals(1, repository.findByDriverId("d-alert").size());
            assertEquals(0, repository.findByDriverId("unknown").size());
        }

        @Test void findFilteredMultiConditions() {
            seed();
            repository.save(alert("a-f1", "FATIGUE", "L3_CRITICAL"));
            repository.save(alert("a-f2", "FATIGUE", "L2_WARNING"));
            repository.save(alert("a-f3", "ROAD_RAGE", "L2_WARNING"));
            List<AlertEventJpaEntity> all = repository.findFiltered(null, null, null);
            assertEquals(3, all.size());
            List<AlertEventJpaEntity> fatigue = repository.findFiltered(null, null, "FATIGUE");
            assertEquals(2, fatigue.size());
            List<AlertEventJpaEntity> l3Fatigue = repository.findFiltered(null, "L3_CRITICAL", "FATIGUE");
            assertEquals(1, l3Fatigue.size());
        }
    }

    @Nested @DisplayName("约束校验")
    class Constraints {
        @Test void missingAlertTypeFails() {
            AlertEventJpaEntity a = new AlertEventJpaEntity();
            a.setAlertId("a-bad");
            a.setTripId("t-alert");
            a.setDriverId("d-alert");
            a.setVehicleId("v-alert");
            a.setRiskLevel("L2_WARNING");
            a.setOccurredAt(LocalDateTime.now());
            assertThrows(DataIntegrityViolationException.class, () -> {
                repository.saveAndFlush(a);
            });
        }
    }

    private AlertEventJpaEntity alert(String id, String type, String level) {
        AlertEventJpaEntity a = new AlertEventJpaEntity();
        a.setAlertId(id);
        a.setTripId("t-alert");
        a.setDriverId("d-alert");
        a.setVehicleId("v-alert");
        a.setAlertType(type);
        a.setRiskLevel(level);
        a.setOccurredAt(LocalDateTime.now());
        return a;
    }
}
