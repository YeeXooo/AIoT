package com.aiot.infra.adapter;

import com.aiot.domain.port.OTADeliveryPort.DeliveryProgress;
import com.aiot.domain.port.OTADeliveryPort.OTADeliveryException;
import com.aiot.domain.port.OTADeliveryPort.OTAPackage;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OTADeliveryAdapterTest {

    private OTADeliveryAdapter adapter;
    private VehicleId vehicleId;

    @BeforeEach
    void setUp() {
        adapter = new OTADeliveryAdapter();
        vehicleId = new VehicleId("veh-1");
    }

    @Test
    void deliverPackage_shouldReturnCompletedProgress() throws OTADeliveryException {
        OTAPackage pkg = new OTAPackage("2.0.0", 1024 * 1024, "abc123", 10);

        DeliveryProgress progress = adapter.deliverPackage(vehicleId, pkg, Optional.empty());

        assertNotNull(progress);
        assertTrue(progress.completed());
        assertEquals(pkg.size(), progress.transferredBytes());
        assertEquals(pkg.size(), progress.totalBytes());
    }

    @Test
    void deliverPackage_withResumeFrom_shouldReturnCompletedProgress() throws OTADeliveryException {
        OTAPackage pkg = new OTAPackage("2.0.0", 2048, "abc123", 5);

        DeliveryProgress progress = adapter.deliverPackage(vehicleId, pkg, Optional.of(1024L));

        assertNotNull(progress);
        assertTrue(progress.completed());
    }

    @Test
    void deliverPackage_shouldReturnFullTransferForSmallPackage() throws OTADeliveryException {
        OTAPackage pkg = new OTAPackage("1.0.1", 256, "aaa111", 1);

        DeliveryProgress progress = adapter.deliverPackage(vehicleId, pkg, Optional.empty());

        assertEquals(256, progress.transferredBytes());
        assertEquals(256, progress.totalBytes());
    }

    @Test
    void cancelDelivery_shouldNotThrow() {
        assertDoesNotThrow(() -> adapter.cancelDelivery(vehicleId));
    }

    @Test
    void cancelDelivery_shouldIdempotent() {
        adapter.cancelDelivery(vehicleId);
        assertDoesNotThrow(() -> adapter.cancelDelivery(vehicleId));
    }

    @Test
    void deliverPackage_withZeroSize_shouldReturnCompleted() throws OTADeliveryException {
        OTAPackage pkg = new OTAPackage("0.0.0", 0, "empty", 0);

        DeliveryProgress progress = adapter.deliverPackage(vehicleId, pkg, Optional.empty());

        assertNotNull(progress);
        assertTrue(progress.completed());
        assertEquals(0, progress.transferredBytes());
        assertEquals(0, progress.totalBytes());
    }
}
