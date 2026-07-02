package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("RoadRageVoiceRecordJpaRepository CRUD")
class RoadRageVoiceRecordJpaRepositoryTest {

    @Autowired private RoadRageVoiceRecordJpaRepository repository;
    @Autowired private TripJpaRepository tripRepo;
    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;

    private void seed() {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId("d-rage"); d.setName("路怒司机"); d.setPhone("13200000001");
        driverRepo.save(d);
        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId("v-rage"); v.setLicensePlate("京R00001"); v.setVin("VINRAGE"); v.setTerminalSn("SN_RAGE");
        vehicleRepo.save(v);
        TripJpaEntity t = new TripJpaEntity();
        t.setTripId("t-rage"); t.setDriverId("d-rage"); t.setVehicleId("v-rage");
        t.setStartedAt(LocalDateTime.now().minusMinutes(30));
        tripRepo.save(t);
    }

    @Nested @DisplayName("基础 CRUD")
    class Crud {
        @Test void saveAndFindById() {
            seed();
            RoadRageVoiceRecordEntity r = record("rr-001");
            repository.save(r);
            RoadRageVoiceRecordEntity found = repository.findById("rr-001").orElseThrow();
            assertEquals("alert-rr-001", found.getAlertId());
            assertEquals("d-rage", found.getDriverId());
            assertEquals("t-rage", found.getTripId());
            assertFalse(found.getIsSealed());
            assertNotNull(found.getExpiryTime());
        }

        @Test void findByDriverId() {
            seed();
            repository.save(record("rr-d1"));
            repository.save(record("rr-d2"));
            List<RoadRageVoiceRecordEntity> result = repository.findByDriverId("d-rage");
            assertEquals(2, result.size());
        }

        @Test void findByIsSealedFalse() {
            seed();
            RoadRageVoiceRecordEntity r1 = record("rr-s1");
            r1.setIsSealed(false);
            RoadRageVoiceRecordEntity r2 = record("rr-s2");
            r2.setIsSealed(true);
            repository.saveAll(List.of(r1, r2));
            List<RoadRageVoiceRecordEntity> unsealed = repository.findByIsSealedFalse();
            assertEquals(1, unsealed.size());
            assertFalse(unsealed.get(0).getIsSealed());
        }

        @Test void sealRecord() {
            seed();
            RoadRageVoiceRecordEntity r = record("rr-seal");
            repository.save(r);
            r.setIsSealed(true);
            r.setEndedAt(LocalDateTime.now());
            repository.save(r);
            RoadRageVoiceRecordEntity sealed = repository.findById("rr-seal").orElseThrow();
            assertTrue(sealed.getIsSealed());
            assertNotNull(sealed.getEndedAt());
        }
    }

    private RoadRageVoiceRecordEntity record(String id) {
        RoadRageVoiceRecordEntity r = new RoadRageVoiceRecordEntity();
        r.setRecordId(id);
        r.setAlertId("alert-" + id);
        r.setTripId("t-rage");
        r.setDriverId("d-rage");
        r.setVehicleId("v-rage");
        r.setStartedAt(LocalDateTime.now().minusMinutes(5));
        r.setExpiryTime(LocalDateTime.now().plusDays(7));
        r.setIsSealed(false);
        return r;
    }
}
