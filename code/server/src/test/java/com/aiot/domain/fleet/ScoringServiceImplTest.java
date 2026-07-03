package com.aiot.domain.fleet;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.DriverScoreUpdatedEvent;
import com.aiot.domain.event.PerformanceWarningEvent;
import com.aiot.domain.event.TripScoredEvent;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.TripScore;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceImplTest {

    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private ScoringServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScoringServiceImpl(tripRepository, driverRepository, eventPublisher);
    }

    @Test
    void calculateTripScoreReturnsScoreForExistingTrip() {
        Trip trip = Trip.start(com.aiot.domain.shared.DriverId.generate(), com.aiot.domain.shared.VehicleId.generate(), LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(0, 0, 1, 0, 0));
        trip.end(LocalDateTime.now().plusHours(1));
        when(tripRepository.findById(trip.tripId().id())).thenReturn(Optional.of(trip));

        Result<TripScore, AppError> result = service.calculateTripScore(trip.tripId().id());

        assertTrue(result.isOk());
        assertTrue(result.unwrap().getValue() > 0);
        verify(eventPublisher).publish(any(TripScoredEvent.class));
        verify(tripRepository).save(trip);
    }

    @Test
    void calculateTripScoreReturnsErrorForMissingTrip() {
        when(tripRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<TripScore, AppError> result = service.calculateTripScore("nonexistent");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void calculateTripScorePublishesWarningWhenScoreBelowThreshold() {
        Trip trip = Trip.start(com.aiot.domain.shared.DriverId.generate(), com.aiot.domain.shared.VehicleId.generate(), LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(10, 10, 10, 10, 10));
        trip.end(LocalDateTime.now().plusHours(1));
        when(tripRepository.findById(trip.tripId().id())).thenReturn(Optional.of(trip));

        Result<TripScore, AppError> result = service.calculateTripScore(trip.tripId().id());

        assertTrue(result.isOk());
        assertTrue(result.unwrap().getValue() < 60);
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void calculatePeriodScoreReturnsDefaultScoreWhenNoTrips() {
        String driverId = "d1";
        TimeRange range = new TimeRange(Instant.now().minusSeconds(7200), Instant.now());
        when(tripRepository.findByDriverId(driverId)).thenReturn(Collections.emptyList());

        Result<Double, AppError> result = service.calculatePeriodScore(driverId, range);

        assertTrue(result.isOk());
        assertEquals(100.0, result.unwrap());
    }

    @Test
    void calculatePeriodScoreFiltersTripsByTimeRange() {
        String driverId = "d1";
        LocalDateTime startedAt = LocalDateTime.now().minusHours(1);
        Trip trip = Trip.start(com.aiot.domain.shared.DriverId.generate(), com.aiot.domain.shared.VehicleId.generate(), startedAt);
        trip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(1, 0, 0, 0, 0));
        trip.end(LocalDateTime.now());
        when(tripRepository.findByDriverId(driverId)).thenReturn(List.of(trip));
        TimeRange range = new TimeRange(
                Instant.now().minusSeconds(7200),
                Instant.now().plusSeconds(3600));

        Result<Double, AppError> result = service.calculatePeriodScore(driverId, range);

        assertTrue(result.isOk());
        assertTrue(result.unwrap() > 0);
    }

    @Test
    void calculatePeriodScoreExcludesTripsOutsideTimeRange() {
        String driverId = "d1";
        LocalDateTime oldStart = LocalDateTime.now().minusDays(10);
        Trip oldTrip = Trip.start(com.aiot.domain.shared.DriverId.generate(), com.aiot.domain.shared.VehicleId.generate(), oldStart);
        oldTrip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(10, 10, 0, 0, 0));
        oldTrip.end(oldStart.plusHours(1));
        when(tripRepository.findByDriverId(driverId)).thenReturn(List.of(oldTrip));
        TimeRange range = new TimeRange(
                Instant.now().minusSeconds(3600),
                Instant.now());

        Result<Double, AppError> result = service.calculatePeriodScore(driverId, range);

        assertTrue(result.isOk());
        assertEquals(100.0, result.unwrap());
    }

    @Test
    void calculateDriverCompositeScoreReturnsScoreForExistingDriver() {
        Driver driver = Driver.create("张三", "13800000001");
        Trip trip = Trip.start(driver.driverId(), com.aiot.domain.shared.VehicleId.generate(), LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(0, 0, 1, 0, 0));
        trip.end(LocalDateTime.now().plusHours(1));
        when(driverRepository.findById(driver.driverId().id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(List.of(trip));

        Result<Double, AppError> result = service.calculateDriverCompositeScore(driver.driverId().id());

        assertTrue(result.isOk());
        assertTrue(result.unwrap() > 0);
        verify(eventPublisher).publish(any(DriverScoreUpdatedEvent.class));
    }

    @Test
    void calculateDriverCompositeScoreReturnsErrorForMissingDriver() {
        when(driverRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<Double, AppError> result = service.calculateDriverCompositeScore("nonexistent");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void calculateDriverCompositeScoreReturnsDefaultForDriverWithNoTrips() {
        Driver driver = Driver.create("张三", "13800000001");
        when(driverRepository.findById(driver.driverId().id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(Collections.emptyList());

        Result<Double, AppError> result = service.calculateDriverCompositeScore(driver.driverId().id());

        assertTrue(result.isOk());
        assertEquals(100.0, result.unwrap());
    }

    @Test
    void calculatePeriodScoreReturnsDefaultWhenTotalDurationIsZero() {
        String driverId = "d1";
        Trip trip = Trip.start(com.aiot.domain.shared.DriverId.generate(), com.aiot.domain.shared.VehicleId.generate(), LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(DrivingBehaviorCounters.of(0, 0, 0, 0, 0));
        when(tripRepository.findByDriverId(driverId)).thenReturn(List.of(trip));
        TimeRange range = new TimeRange(Instant.now().minusSeconds(60), Instant.now());

        Result<Double, AppError> result = service.calculatePeriodScore(driverId, range);

        assertTrue(result.isOk());
        assertEquals(100.0, result.unwrap());
    }
}
