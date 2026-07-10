package com.aiot.domain.risk;

import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrivingBehaviorTrackingServiceImplTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private Trip trip;

    private DrivingBehaviorTrackingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DrivingBehaviorTrackingServiceImpl(tripRepository);
    }

    @Test
    void onHardBrakingIncrementsAndSaves() {
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(trip));
        when(trip.drivingBehaviorCounters()).thenReturn(counters);

        service.onHardBrakingDetected("trip-1", 1700000000L, 0.5);

        verify(trip).updateDrivingBehaviorCounters(any());
        verify(tripRepository).save(trip);
    }

    @Test
    void onHardBrakingNoOpWhenTripNotFound() {
        when(tripRepository.findById("trip-x")).thenReturn(Optional.empty());

        service.onHardBrakingDetected("trip-x", 1700000000L, 0.5);

        verify(tripRepository, never()).save(any());
    }

    @Test
    void onHardAccelerationIncrementsAndSaves() {
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(trip));
        when(trip.drivingBehaviorCounters()).thenReturn(counters);

        service.onHardAccelerationDetected("trip-1", 1700000000L, 0.8);

        verify(trip).updateDrivingBehaviorCounters(any());
        verify(tripRepository).save(trip);
    }

    @Test
    void onHardAccelerationNoOpWhenTripNotFound() {
        when(tripRepository.findById("trip-x")).thenReturn(Optional.empty());

        service.onHardAccelerationDetected("trip-x", 1700000000L, 0.8);

        verify(tripRepository, never()).save(any());
    }
}
