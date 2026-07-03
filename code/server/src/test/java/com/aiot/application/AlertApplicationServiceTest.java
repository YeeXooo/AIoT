package com.aiot.application;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.GeoLocation;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.repository.AlertEventRepository;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertApplicationServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    private AlertApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AlertApplicationService(alertEventRepository);
    }

    @Test
    void listAlertsShouldPassNullsWhenAllParamsNull() {
        when(alertEventRepository.findFiltered(null, null, null)).thenReturn(List.of());

        var result = service.listAlerts(null, null, null);

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered(null, null, null);
    }

    @Test
    void listAlertsShouldPassNullsWhenAllParamsEmpty() {
        when(alertEventRepository.findFiltered(null, null, null)).thenReturn(List.of());

        var result = service.listAlerts("", "", "");

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered(null, null, null);
    }

    @Test
    void listAlertsShouldPassValidParamsToFindFiltered() {
        var alert = SafetyAlertEvent.reconstitute(
                new AlertId("alert-1"), new TripId("trip-1"), new DriverId("driver-1"),
                new VehicleId("vehicle-1"), AlertType.FATIGUE, RiskLevel.L2_WARNING,
                LocalDateTime.now(), "Driver fatigue detected", false, null);
        when(alertEventRepository.findFiltered("driver-1", "L2_WARNING", "FATIGUE"))
                .thenReturn(List.of(alert));

        var result = service.listAlerts("driver-1", "L2_WARNING", "FATIGUE");

        assertEquals(1, result.size());
        assertEquals(new AlertId("alert-1"), result.get(0).alertId());
        verify(alertEventRepository, times(1)).findFiltered("driver-1", "L2_WARNING", "FATIGUE");
    }

    @Test
    void listAlertsShouldReturnMultipleResults() {
        var alert1 = SafetyAlertEvent.reconstitute(
                new AlertId("alert-1"), new TripId("trip-1"), new DriverId("driver-1"),
                new VehicleId("vehicle-1"), AlertType.FATIGUE, RiskLevel.L2_WARNING,
                LocalDateTime.now(), "Fatigue detected", false, null);
        var alert2 = SafetyAlertEvent.reconstitute(
                new AlertId("alert-2"), new TripId("trip-2"), new DriverId("driver-1"),
                new VehicleId("vehicle-2"), AlertType.DISTRACTION, RiskLevel.L3_CRITICAL,
                LocalDateTime.now(), "Distraction detected", false, null);
        when(alertEventRepository.findFiltered("driver-1", null, null))
                .thenReturn(List.of(alert1, alert2));

        var result = service.listAlerts("driver-1", null, null);

        assertEquals(2, result.size());
        verify(alertEventRepository, times(1)).findFiltered("driver-1", null, null);
    }

    @Test
    void listAlertsShouldReturnEmptyListWhenNoAlertsMatch() {
        when(alertEventRepository.findFiltered("unknown", null, null)).thenReturn(List.of());

        var result = service.listAlerts("unknown", null, null);

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered("unknown", null, null);
    }

    @Test
    void listAlertsShouldTreatEmptyDriverIdAsNull() {
        when(alertEventRepository.findFiltered(null, null, null)).thenReturn(List.of());

        var result = service.listAlerts("", null, null);

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered(null, null, null);
    }

    @Test
    void listAlertsShouldTreatEmptyRiskLevelAsNull() {
        when(alertEventRepository.findFiltered("driver-1", null, null)).thenReturn(List.of());

        var result = service.listAlerts("driver-1", "", null);

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered("driver-1", null, null);
    }

    @Test
    void listAlertsShouldTreatEmptyAlertTypeAsNull() {
        when(alertEventRepository.findFiltered("driver-1", "L2_WARNING", null)).thenReturn(List.of());

        var result = service.listAlerts("driver-1", "L2_WARNING", "");

        assertTrue(result.isEmpty());
        verify(alertEventRepository, times(1)).findFiltered("driver-1", "L2_WARNING", null);
    }
}
