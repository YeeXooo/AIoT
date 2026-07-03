package com.aiot.application.intervention;

import com.aiot.domain.intervention.InterventionService;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.model.OverrideType;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterventionServiceImplTest {

    @Mock
    private InterventionService interventionService;
    @Mock
    private TripRepository tripRepository;

    private InterventionServiceImpl service;

    private static final DriverId DRIVER_ID = new DriverId("driver-1");
    private static final TripId TRIP_ID = new TripId("trip-1");
    private static final VehicleId VEHICLE_ID = new VehicleId("vehicle-1");

    @BeforeEach
    void setUp() {
        service = new InterventionServiceImpl(interventionService, tripRepository);
    }

    private Trip createTrip() {
        return Trip.reconstitute(TRIP_ID, DRIVER_ID, VEHICLE_ID,
                LocalDateTime.now(), null, 0, 0, null, 1,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private OverrideSignal createSignal(OverrideType type) {
        return OverrideSignal.of(type, Instant.now());
    }

    // ========== reportOverride ==========

    @Test
    void reportOverrideShouldReturnOkWhenAborted() {
        Trip trip = createTrip();
        OverrideSignal signal = createSignal(OverrideType.BRAKING);
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(signal))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.ABORTED, 1000L));

        Result<IInterventionService.ReportOverrideResponse, AppError> result =
                service.reportOverride(DRIVER_ID, signal);

        assertTrue(result.isOk());
        IInterventionService.ReportOverrideResponse resp = result.unwrap();
        assertEquals("ABORTED", resp.status());
        assertEquals(1000L, resp.timestamp());
        verify(tripRepository).findActiveTrips();
        verify(interventionService).handleOverride(signal);
    }

    @Test
    void reportOverrideShouldReturnOkWhenContinuing() {
        Trip trip = createTrip();
        OverrideSignal signal = createSignal(OverrideType.TURNING);
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(signal))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.CONTINUING, 2000L));

        Result<IInterventionService.ReportOverrideResponse, AppError> result =
                service.reportOverride(DRIVER_ID, signal);

        assertTrue(result.isOk());
        IInterventionService.ReportOverrideResponse resp = result.unwrap();
        assertEquals("CONTINUING", resp.status());
    }

    @Test
    void reportOverrideShouldReturnErrWhenNoActiveTrips() {
        OverrideSignal signal = createSignal(OverrideType.ACCELERATING);
        when(tripRepository.findActiveTrips()).thenReturn(List.of());

        Result<IInterventionService.ReportOverrideResponse, AppError> result =
                service.reportOverride(DRIVER_ID, signal);

        assertTrue(result.isErr());
        AppError error = result.unwrapErr();
        assertEquals("NotFound", error.code());
        assertTrue(error.message().contains("driver-1"));
        verify(interventionService, never()).handleOverride(any());
    }

    @Test
    void reportOverrideShouldReturnErrWhenActiveTripsExistButNotForDriver() {
        Trip otherTrip = Trip.reconstitute(new TripId("trip-2"), new DriverId("driver-2"),
                VEHICLE_ID, LocalDateTime.now(), null, 0, 0, null, 1,
                LocalDateTime.now(), LocalDateTime.now());
        OverrideSignal signal = createSignal(OverrideType.RESUMING);
        when(tripRepository.findActiveTrips()).thenReturn(List.of(otherTrip));

        Result<IInterventionService.ReportOverrideResponse, AppError> result =
                service.reportOverride(DRIVER_ID, signal);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void reportOverrideShouldPropagateExceptionFromDomainService() {
        Trip trip = createTrip();
        OverrideSignal signal = createSignal(OverrideType.BRAKING);
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(signal))
                .thenThrow(new RuntimeException("domain failure"));

        assertThrows(RuntimeException.class, () -> service.reportOverride(DRIVER_ID, signal));
    }

    // ========== queryInterventionStatus ==========

    @Test
    void queryInterventionStatusShouldReturnLatestRecord() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));

        service.reportOverride(DRIVER_ID, createSignal(OverrideType.BRAKING));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(any()))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.CONTINUING, 3000L));
        service.reportOverride(DRIVER_ID, createSignal(OverrideType.TURNING));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionStatus(TRIP_ID);

        assertTrue(result.isOk());
        IInterventionService.QueryInterventionResponse resp = result.unwrap();
        assertEquals(TRIP_ID.id(), resp.tripId());
        assertEquals(1, resp.items().size());
        assertEquals("OVERRIDE", resp.items().get(0).status());
    }

    @Test
    void queryInterventionStatusShouldReturnErrWhenTripNotFound() {
        when(tripRepository.findById("unknown")).thenReturn(java.util.Optional.empty());

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionStatus(new TripId("unknown"));

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void queryInterventionStatusShouldReturnEmptyItemsWhenNoRecords() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionStatus(TRIP_ID);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().items().size());
    }

    // ========== queryInterventionHistory ==========

    @Test
    void queryInterventionHistoryShouldReturnPagedResults() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(any()))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.ABORTED, 1000L))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.CONTINUING, 2000L))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.RESUMED, 3000L));
        service.reportOverride(DRIVER_ID, createSignal(OverrideType.BRAKING));
        service.reportOverride(DRIVER_ID, createSignal(OverrideType.TURNING));
        service.reportOverride(DRIVER_ID, createSignal(OverrideType.RESUMING));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionHistory(TRIP_ID, 0, 2);

        assertTrue(result.isOk());
        IInterventionService.QueryInterventionResponse resp = result.unwrap();
        assertEquals(2, resp.items().size());
        assertEquals(3, resp.totalCount());
        assertEquals(0, resp.page());
        assertEquals(2, resp.size());
    }

    @Test
    void queryInterventionHistoryShouldReturnEmptyItemsWhenPageBeyondRange() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionHistory(TRIP_ID, 5, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().items().isEmpty());
    }

    @Test
    void queryInterventionHistoryShouldReturnErrWhenTripNotFound() {
        when(tripRepository.findById("unknown")).thenReturn(java.util.Optional.empty());

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionHistory(new TripId("unknown"), 0, 10);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void queryInterventionHistoryShouldReturnEmptyListWhenNoRecords() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionHistory(TRIP_ID, 0, 10);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().totalCount());
    }

    @Test
    void queryInterventionHistoryShouldHandlePartialLastPage() {
        Trip trip = createTrip();
        when(tripRepository.findById(TRIP_ID.id())).thenReturn(java.util.Optional.of(trip));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));
        when(interventionService.handleOverride(any()))
                .thenReturn(new InterventionService.InterventionResult(
                        InterventionService.OverrideResult.ABORTED, 1000L));
        service.reportOverride(DRIVER_ID, createSignal(OverrideType.BRAKING));

        Result<IInterventionService.QueryInterventionResponse, AppError> result =
                service.queryInterventionHistory(TRIP_ID, 0, 5);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().items().size());
    }
}
