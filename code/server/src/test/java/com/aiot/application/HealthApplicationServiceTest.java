package com.aiot.application;

import com.aiot.infra.persistence.DriverHealthProfileEntity;
import com.aiot.infra.repository.DriverHealthProfileJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthApplicationServiceTest {

    @Mock
    private DriverHealthProfileJpaRepository repo;

    private HealthApplicationService service;

    @BeforeEach
    void setUp() {
        service = new HealthApplicationService(repo);
    }

    @Test
    void getShouldReturnProfileWhenFound() {
        var entity = new DriverHealthProfileEntity();
        entity.setDriverId("driver-1");
        entity.setBloodType("A");
        when(repo.findById("driver-1")).thenReturn(Optional.of(entity));

        var result = service.get("driver-1");

        assertTrue(result.isPresent());
        assertEquals("driver-1", result.get().getDriverId());
        assertEquals("A", result.get().getBloodType());
        verify(repo, times(1)).findById("driver-1");
    }

    @Test
    void getShouldReturnEmptyWhenNotFound() {
        when(repo.findById("unknown")).thenReturn(Optional.empty());

        var result = service.get("unknown");

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findById("unknown");
    }

    @Test
    void getShouldReturnEmptyForNullDriverId() {
        when(repo.findById(null)).thenReturn(Optional.empty());

        var result = service.get(null);

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findById(null);
    }

    @Test
    void getShouldReturnEmptyForEmptyDriverId() {
        when(repo.findById("")).thenReturn(Optional.empty());

        var result = service.get("");

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findById("");
    }

    @Test
    void saveShouldReturnSavedEntity() {
        var entity = new DriverHealthProfileEntity();
        entity.setDriverId("driver-2");
        entity.setBloodType("B");
        when(repo.save(entity)).thenReturn(entity);

        var result = service.save(entity);

        assertNotNull(result);
        assertEquals("driver-2", result.getDriverId());
        assertEquals("B", result.getBloodType());
        verify(repo, times(1)).save(entity);
    }

    @Test
    void saveShouldPersistProfileWithAllFields() {
        var entity = new DriverHealthProfileEntity();
        entity.setDriverId("driver-3");
        entity.setBloodType("O");
        entity.setAllergyHistory("Pollen");
        entity.setChronicHistory("None");
        entity.setMedicationHistory("None");
        entity.setBaselineVitals("Normal");
        entity.setEmergencyContact("John Doe: 123456");
        when(repo.save(entity)).thenReturn(entity);

        var result = service.save(entity);

        assertNotNull(result);
        assertEquals("Pollen", result.getAllergyHistory());
        assertEquals("None", result.getChronicHistory());
        assertEquals("Normal", result.getBaselineVitals());
        assertEquals("John Doe: 123456", result.getEmergencyContact());
        verify(repo, times(1)).save(entity);
    }
}
