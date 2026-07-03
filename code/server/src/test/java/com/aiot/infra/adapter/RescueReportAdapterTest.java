package com.aiot.infra.adapter;

import com.aiot.domain.model.GeoLocation;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.RescueReport;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.port.RescueReportPort.RescueReportException;
import com.aiot.domain.shared.DriverId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RescueReportAdapterTest {

    private RescueReportAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RescueReportAdapter();
    }

    @Test
    void deliverRescueReport_shouldNotThrowForValidReport() {
        RescueReport report = createValidReport();

        assertDoesNotThrow(() -> adapter.deliverRescueReport(report));
    }

    @Test
    void deliverRescueReport_withNullVitals_shouldNotThrow() {
        RescueReport report = new RescueReport(
                new DriverId("drv-1"),
                new GeoLocation(31.0, 121.0),
                null,
                null,
                "summary",
                Instant.now()
        );

        assertDoesNotThrow(() -> adapter.deliverRescueReport(report));
    }

    @Test
    void deliverRescueReport_withEmptyVehicleStates_shouldNotThrow() {
        RescueReport report = new RescueReport(
                new DriverId("drv-1"),
                new GeoLocation(31.0, 121.0),
                null,
                List.of(),
                "summary",
                Instant.now()
        );

        assertDoesNotThrow(() -> adapter.deliverRescueReport(report));
    }

    @Test
    void deliverRescueReport_withFullData_shouldNotThrow() {
        RescueReport report = createValidReport();

        assertDoesNotThrow(() -> adapter.deliverRescueReport(report));
    }

    @Test
    void deliverRescueReport_withNullHealthSummary_shouldNotThrow() {
        RescueReport report = new RescueReport(
                new DriverId("drv-1"),
                new GeoLocation(31.0, 121.0),
                null,
                null,
                null,
                Instant.now()
        );

        assertDoesNotThrow(() -> adapter.deliverRescueReport(report));
    }

    private RescueReport createValidReport() {
        PhysiologicalSnapshot vitals = new PhysiologicalSnapshot(
                Instant.now(), 72, 98.0, 0.5, 16, 120, 80, 0.3, 36.5);

        VehicleStateSnapshot state = new VehicleStateSnapshot(
                Instant.now(), 60.0, 0.0, true, false, false, 31.0, 121.0);

        return new RescueReport(
                new DriverId("drv-1"),
                new GeoLocation(31.0, 121.0),
                vitals,
                List.of(state),
                "Driver is unconscious",
                Instant.now()
        );
    }
}
