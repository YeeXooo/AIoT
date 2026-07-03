package com.aiot.infra.adapter;

import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.port.BufferException;
import com.aiot.domain.port.BufferException.WindowNotCoveredException;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleStateBufferAdapterTest {

    private VehicleStateBufferAdapter adapter;
    private TripId tripId;

    @BeforeEach
    void setUp() {
        adapter = new VehicleStateBufferAdapter(5);
        tripId = new TripId("trip-1");
    }

    @Test
    void addSnapshot_shouldAddToBuffer() {
        VehicleStateSnapshot snapshot = createSnapshot(Instant.now(), 60.0);
        adapter.addSnapshot(snapshot);

        assertEquals(1, adapter.size());
    }

    @Test
    void addSnapshot_shouldRolloverWhenFull() {
        for (int i = 0; i < 7; i++) {
            adapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 60.0));
        }

        assertEquals(5, adapter.size());
    }

    @Test
    void getSnapshots_shouldReturnMatchingWindow() throws BufferException {
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            adapter.addSnapshot(createSnapshot(base.plusSeconds(i), 50.0 + i));
        }

        TimeRange window = new TimeRange(base.plusSeconds(1), base.plusSeconds(3));
        List<VehicleStateSnapshot> results = adapter.getSnapshots(tripId, window);

        assertEquals(3, results.size());
    }

    @Test
    void getSnapshots_shouldThrowWhenBufferIsEmpty() {
        TimeRange window = new TimeRange(Instant.now(), Instant.now().plusSeconds(5));

        assertThrows(WindowNotCoveredException.class,
                () -> adapter.getSnapshots(tripId, window));
    }

    @Test
    void getSnapshots_shouldThrowWhenWindowStartsBeforeOldest() {
        Instant now = Instant.now();
        adapter.addSnapshot(createSnapshot(now, 60.0));

        TimeRange window = new TimeRange(now.minusSeconds(10), now.plusSeconds(5));

        assertThrows(WindowNotCoveredException.class,
                () -> adapter.getSnapshots(tripId, window));
    }

    @Test
    void getSnapshots_shouldReturnEmptyListWhenNoSnapshotsInWindow() throws BufferException {
        Instant now = Instant.now();
        adapter.addSnapshot(createSnapshot(now, 60.0));

        TimeRange window = new TimeRange(now.plusSeconds(10), now.plusSeconds(20));
        List<VehicleStateSnapshot> results = adapter.getSnapshots(tripId, window);

        assertTrue(results.isEmpty());
    }

    @Test
    void clear_shouldRemoveAllSnapshots() {
        adapter.addSnapshot(createSnapshot(Instant.now(), 60.0));
        adapter.addSnapshot(createSnapshot(Instant.now(), 61.0));
        adapter.clear();

        assertEquals(0, adapter.size());
    }

    @Test
    void size_shouldReturnInitialZero() {
        assertEquals(0, adapter.size());
    }

    @Test
    void defaultConstructor_shouldUseDefaultCapacity() {
        VehicleStateBufferAdapter defaultAdapter = new VehicleStateBufferAdapter();

        for (int i = 0; i < 350; i++) {
            defaultAdapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 60.0));
        }

        assertTrue(defaultAdapter.size() <= 300);
    }

    @Test
    void customCapacity_shouldLimitBufferSize() {
        VehicleStateBufferAdapter customAdapter = new VehicleStateBufferAdapter(10);

        for (int i = 0; i < 15; i++) {
            customAdapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 60.0));
        }

        assertEquals(10, customAdapter.size());
    }

    @Test
    void getSnapshots_shouldIncludeWindowBoundaries() throws BufferException {
        Instant now = Instant.now();
        Instant from = now.plusSeconds(2);
        Instant to = now.plusSeconds(4);

        adapter.addSnapshot(createSnapshot(from, 60.0));
        adapter.addSnapshot(createSnapshot(to, 61.0));

        TimeRange window = new TimeRange(from, to);
        List<VehicleStateSnapshot> results = adapter.getSnapshots(tripId, window);

        assertEquals(2, results.size());
        assertEquals(60.0, results.get(0).speed());
        assertEquals(61.0, results.get(1).speed());
    }

    private VehicleStateSnapshot createSnapshot(Instant timestamp, double speed) {
        return new VehicleStateSnapshot(
                timestamp, speed, 0.0, true, false, false, 31.0, 121.0);
    }
}
