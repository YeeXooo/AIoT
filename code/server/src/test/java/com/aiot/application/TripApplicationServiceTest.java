package com.aiot.application;

import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripApplicationServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private TripApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripApplicationService(tripRepository, vehicleRepository);
    }

    @Test
    void listTripsShouldReturnActiveTripsWhenActiveTrue() {
        var trip = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));

        var result = service.listTrips(null, true);

        assertEquals(1, result.size());
        verify(tripRepository, times(1)).findActiveTrips();
        verify(tripRepository, never()).findByDriverId(any());
        verify(tripRepository, never()).findAll();
    }

    @Test
    void listTripsShouldReturnActiveTripsEvenWhenDriverIdProvided() {
        var trip = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));

        var result = service.listTrips("driver-1", true);

        assertEquals(1, result.size());
        verify(tripRepository, times(1)).findActiveTrips();
        verify(tripRepository, never()).findByDriverId(any());
    }

    @Test
    void listTripsShouldReturnByDriverIdWhenActiveNotTrue() {
        var trip = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        when(tripRepository.findByDriverId("driver-1")).thenReturn(List.of(trip));

        var result = service.listTrips("driver-1", false);

        assertEquals(1, result.size());
        verify(tripRepository, times(1)).findByDriverId("driver-1");
    }

    @Test
    void listTripsShouldReturnByDriverIdWhenActiveNull() {
        var trip = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        when(tripRepository.findByDriverId("driver-1")).thenReturn(List.of(trip));

        var result = service.listTrips("driver-1", null);

        assertEquals(1, result.size());
        verify(tripRepository, times(1)).findByDriverId("driver-1");
    }

    @Test
    void listTripsShouldReturnAllWhenNoParams() {
        var trip1 = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        var trip2 = Trip.start(new DriverId("driver-2"), new VehicleId("vehicle-2"), LocalDateTime.now());
        when(tripRepository.findAll()).thenReturn(List.of(trip1, trip2));

        var result = service.listTrips(null, null);

        assertEquals(2, result.size());
        verify(tripRepository, times(1)).findAll();
    }

    @Test
    void listTripsShouldReturnAllWhenActiveFalse() {
        var trip = Trip.start(new DriverId("driver-1"), new VehicleId("vehicle-1"), LocalDateTime.now());
        when(tripRepository.findAll()).thenReturn(List.of(trip));

        var result = service.listTrips(null, false);

        assertEquals(1, result.size());
        verify(tripRepository, times(1)).findAll();
    }

    @Test
    void listTripsShouldReturnEmptyListWhenNoTrips() {
        when(tripRepository.findAll()).thenReturn(List.of());

        var result = service.listTrips(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void listTripsShouldReturnEmptyWhenDriverIdEmpty() {
        when(tripRepository.findAll()).thenReturn(List.of());

        var result = service.listTrips("", null);

        assertTrue(result.isEmpty());
        verify(tripRepository, times(1)).findAll();
    }

    @Test
    void listVehiclesShouldFilterByKeywordWhenProvided() {
        var vehicle = Vehicle.register("ABC123", "VIN12345678901234", "SN12345");
        when(vehicleRepository.findByLicensePlateLike("ABC")).thenReturn(List.of(vehicle));

        var result = service.listVehicles(null, "ABC");

        assertEquals(1, result.size());
        verify(vehicleRepository, times(1)).findByLicensePlateLike("ABC");
    }

    @Test
    void listVehiclesShouldFilterByKeywordOverFleetId() {
        var vehicle = Vehicle.register("ABC123", "VIN12345678901234", "SN12345");
        when(vehicleRepository.findByLicensePlateLike("ABC")).thenReturn(List.of(vehicle));

        var result = service.listVehicles("fleet-1", "ABC");

        assertEquals(1, result.size());
        verify(vehicleRepository, times(1)).findByLicensePlateLike("ABC");
        verify(vehicleRepository, never()).findByFleetId(any());
    }

    @Test
    void listVehiclesShouldFilterByFleetIdWhenNoKeyword() {
        var vehicle = Vehicle.register("ABC123", "VIN12345678901234", "SN12345");
        when(vehicleRepository.findByFleetId("fleet-1")).thenReturn(List.of(vehicle));

        var result = service.listVehicles("fleet-1", null);

        assertEquals(1, result.size());
        verify(vehicleRepository, times(1)).findByFleetId("fleet-1");
    }

    @Test
    void listVehiclesShouldReturnAllWhenNoParams() {
        var vehicle1 = Vehicle.register("ABC123", "VIN11111111111111", "SN11111");
        var vehicle2 = Vehicle.register("XYZ789", "VIN22222222222222", "SN22222");
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle1, vehicle2));

        var result = service.listVehicles(null, null);

        assertEquals(2, result.size());
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void listVehiclesShouldReturnAllWhenKeywordEmpty() {
        var vehicle = Vehicle.register("ABC123", "VIN12345678901234", "SN12345");
        when(vehicleRepository.findAll()).thenReturn(List.of(vehicle));

        var result = service.listVehicles(null, "");

        assertEquals(1, result.size());
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void listVehiclesShouldReturnEmptyWhenNoVehicles() {
        when(vehicleRepository.findAll()).thenReturn(List.of());

        var result = service.listVehicles(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void listVehiclesShouldReturnEmptyForUnknownFleetId() {
        when(vehicleRepository.findByFleetId("unknown")).thenReturn(List.of());

        var result = service.listVehicles("unknown", null);

        assertTrue(result.isEmpty());
    }
}
