package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("VehicleJpaRepository CRUD")
class VehicleJpaRepositoryTest {

    @Autowired private VehicleJpaRepository repository;

    private VehicleJpaEntity vehicle(String id, String plate, String vin, String sn) {
        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId(id); v.setLicensePlate(plate); v.setVin(vin); v.setTerminalSn(sn);
        v.setFirmwareVersion("1.0.0");
        return v;
    }

    @Nested @DisplayName("基础 CRUD")
    class Crud {
        @Test void saveAndFindById() {
            repository.save(vehicle("v-001", "京A10001", "VIN-10001", "SN-10001"));
            VehicleJpaEntity found = repository.findById("v-001").orElseThrow();
            assertEquals("京A10001", found.getLicensePlate());
            assertEquals("VIN-10001", found.getVin());
            assertEquals("SN-10001", found.getTerminalSn());
            assertNotNull(found.getCreatedAt());
        }

        @Test void findByFleetId() {
            VehicleJpaEntity v1 = vehicle("v-f1", "京A20001", "VIN-F1", "SN-F1");
            v1.setFleetId("fleet-east");
            VehicleJpaEntity v2 = vehicle("v-f2", "京A20002", "VIN-F2", "SN-F2");
            v2.setFleetId("fleet-east");
            VehicleJpaEntity v3 = vehicle("v-f3", "京A20003", "VIN-F3", "SN-F3");
            v3.setFleetId("fleet-west");
            repository.saveAll(List.of(v1, v2, v3));
            assertEquals(2, repository.findByFleetId("fleet-east").size());
            assertEquals(1, repository.findByFleetId("fleet-west").size());
        }

        @Test void findByLicensePlateLike() {
            repository.save(vehicle("v1", "京A12345", "V01", "S01"));
            repository.save(vehicle("v2", "京B12346", "V02", "S02"));
            List<VehicleJpaEntity> result = repository.findByLicensePlateLike("京A");
            assertEquals(1, result.size());
            assertEquals("京A12345", result.get(0).getLicensePlate());
        }
    }

    @Nested @DisplayName("约束校验")
    class Constraints {
        @Test void missingVinFails() {
            VehicleJpaEntity v = new VehicleJpaEntity();
            v.setVehicleId("v-bad");
            v.setLicensePlate("京C00000");
            v.setTerminalSn("SN-BAD");
            assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(v));
        }
    }
}
