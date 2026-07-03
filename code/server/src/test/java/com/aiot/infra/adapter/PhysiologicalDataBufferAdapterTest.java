package com.aiot.infra.adapter;

import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.port.BufferException;
import com.aiot.domain.port.BufferException.WindowNotCoveredException;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhysiologicalDataBufferAdapterTest {

    private PhysiologicalDataBufferAdapter adapter;
    private TripId tripId;

    @BeforeEach
    void setUp() {
        adapter = new PhysiologicalDataBufferAdapter(5);
        tripId = new TripId("trip-1");
    }

    @Test
    void addSnapshot_shouldAddToBuffer() {
        PhysiologicalSnapshot snapshot = createSnapshot(Instant.now(), 72);
        adapter.addSnapshot(snapshot);

        assertEquals(1, adapter.size());
    }

    @Test
    void addSnapshot_shouldRolloverWhenFull() {
        for (int i = 0; i < 7; i++) {
            adapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 72 + i));
        }

        assertEquals(5, adapter.size());
    }

    @Test
    void getReadings_shouldReturnMatchingWindow() throws BufferException {
        Instant base = Instant.now();
        for (int i = 0; i < 5; i++) {
            adapter.addSnapshot(createSnapshot(base.plusSeconds(i), 70 + i));
        }

        TimeRange window = new TimeRange(base.plusSeconds(1), base.plusSeconds(3));
        List<PhysiologicalSnapshot> results = adapter.getReadings(tripId, window);

        assertEquals(3, results.size());
    }

    @Test
    void getReadings_shouldThrowWhenBufferIsEmpty() {
        TimeRange window = new TimeRange(Instant.now(), Instant.now().plusSeconds(5));

        assertThrows(WindowNotCoveredException.class,
                () -> adapter.getReadings(tripId, window));
    }

    @Test
    void getReadings_shouldThrowWhenWindowStartsBeforeOldest() {
        Instant now = Instant.now();
        adapter.addSnapshot(createSnapshot(now, 72));

        TimeRange window = new TimeRange(now.minusSeconds(10), now.plusSeconds(5));

        assertThrows(WindowNotCoveredException.class,
                () -> adapter.getReadings(tripId, window));
    }

    @Test
    void getReadings_shouldReturnEmptyListWhenNoSnapshotsInWindow() throws BufferException {
        Instant now = Instant.now();
        adapter.addSnapshot(createSnapshot(now, 72));

        TimeRange window = new TimeRange(now.plusSeconds(10), now.plusSeconds(20));
        List<PhysiologicalSnapshot> results = adapter.getReadings(tripId, window);

        assertTrue(results.isEmpty());
    }

    @Test
    void clear_shouldRemoveAllSnapshots() {
        adapter.addSnapshot(createSnapshot(Instant.now(), 72));
        adapter.addSnapshot(createSnapshot(Instant.now(), 73));
        adapter.clear();

        assertEquals(0, adapter.size());
    }

    @Test
    void size_shouldReturnInitialZero() {
        assertEquals(0, adapter.size());
    }

    @Test
    void defaultConstructor_shouldUseDefaultCapacity() {
        PhysiologicalDataBufferAdapter defaultAdapter = new PhysiologicalDataBufferAdapter();

        for (int i = 0; i < 150; i++) {
            defaultAdapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 72));
        }

        assertTrue(defaultAdapter.size() <= 100);
    }

    @Test
    void customCapacity_shouldLimitBufferSize() {
        PhysiologicalDataBufferAdapter customAdapter = new PhysiologicalDataBufferAdapter(10);

        for (int i = 0; i < 15; i++) {
            customAdapter.addSnapshot(createSnapshot(Instant.now().plusSeconds(i), 72));
        }

        assertEquals(10, customAdapter.size());
    }

    @Test
    void getReadings_shouldIncludeWindowBoundaries() throws BufferException {
        Instant now = Instant.now();
        Instant from = now.plusSeconds(2);
        Instant to = now.plusSeconds(4);

        adapter.addSnapshot(createSnapshot(from, 70));
        adapter.addSnapshot(createSnapshot(to, 71));

        TimeRange window = new TimeRange(from, to);
        List<PhysiologicalSnapshot> results = adapter.getReadings(tripId, window);

        assertEquals(2, results.size());
        assertEquals(70, results.get(0).heartRate());
        assertEquals(71, results.get(1).heartRate());
    }

    @Test
    void getReadings_shouldReturnFirstSnapshotThatMatches() throws BufferException {
        Instant now = Instant.now();
        adapter.addSnapshot(createSnapshot(now.plusSeconds(1), 72));
        adapter.addSnapshot(createSnapshot(now.plusSeconds(2), 74));
        adapter.addSnapshot(createSnapshot(now.plusSeconds(3), 76));

        TimeRange window = new TimeRange(now.plusSeconds(2), now.plusSeconds(10));
        List<PhysiologicalSnapshot> results = adapter.getReadings(tripId, window);

        assertEquals(2, results.size());
        assertEquals(74, results.get(0).heartRate());
        assertEquals(76, results.get(1).heartRate());
    }

    private PhysiologicalSnapshot createSnapshot(Instant timestamp, int heartRate) {
        return new PhysiologicalSnapshot(
                timestamp, heartRate, 98.0, 0.5, 16, 120, 80, 0.3, 36.5);
    }
}
