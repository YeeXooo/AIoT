package com.aiot.application;

import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverApplicationServiceTest {

    @Mock
    private DriverRepository driverRepository;

    private DriverApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DriverApplicationService(driverRepository);
    }

    @Test
    void listShouldFilterByNameWhenProvided() {
        var driver = Driver.create("John Doe", "1234567890");
        when(driverRepository.findByNameLike("John")).thenReturn(List.of(driver));

        var result = service.list("John");

        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).name());
        verify(driverRepository, times(1)).findByNameLike("John");
    }

    @Test
    void listShouldReturnAllWhenNameIsNull() {
        var driver1 = Driver.create("Alice", "1111111111");
        var driver2 = Driver.create("Bob", "2222222222");
        when(driverRepository.findAll()).thenReturn(List.of(driver1, driver2));

        var result = service.list(null);

        assertEquals(2, result.size());
        verify(driverRepository, times(1)).findAll();
    }

    @Test
    void listShouldReturnAllWhenNameIsEmpty() {
        var driver = Driver.create("Alice", "1111111111");
        when(driverRepository.findAll()).thenReturn(List.of(driver));

        var result = service.list("");

        assertEquals(1, result.size());
        verify(driverRepository, times(1)).findAll();
        verify(driverRepository, never()).findByNameLike(any());
    }

    @Test
    void listShouldReturnEmptyWhenNoDrivers() {
        when(driverRepository.findAll()).thenReturn(List.of());

        var result = service.list(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void listShouldReturnEmptyWhenNameNotFound() {
        when(driverRepository.findByNameLike("Unknown")).thenReturn(List.of());

        var result = service.list("Unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void addShouldSaveDriver() {
        var driver = Driver.create("Jane Doe", "0987654321");
        doNothing().when(driverRepository).save(driver);

        service.add(driver);

        verify(driverRepository, times(1)).save(driver);
    }

    @Test
    void addShouldSaveMultipleDrivers() {
        var driver1 = Driver.create("Alice", "1111111111");
        var driver2 = Driver.create("Bob", "2222222222");

        service.add(driver1);
        service.add(driver2);

        verify(driverRepository, times(1)).save(driver1);
        verify(driverRepository, times(1)).save(driver2);
    }

    @Test
    void updateShouldSaveDriver() {
        var driver = Driver.create("Alice", "1111111111");
        doNothing().when(driverRepository).save(driver);

        service.update(driver);

        verify(driverRepository, times(1)).save(driver);
    }

    @Test
    void deleteShouldDeleteDriverById() {
        doNothing().when(driverRepository).delete("driver-1");

        service.delete("driver-1");

        verify(driverRepository, times(1)).delete("driver-1");
    }

    @Test
    void deleteShouldHandleNullId() {
        doNothing().when(driverRepository).delete(null);

        service.delete(null);

        verify(driverRepository, times(1)).delete(null);
    }
}
