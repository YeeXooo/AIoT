package com.aiot.infra.adapter;

import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.port.BufferException;
import com.aiot.domain.port.PhysiologicalDataBuffer;
import com.aiot.domain.shared.TripId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 生理数据滚动缓冲适配器。
 * <p>
 * 基于 ArrayDeque 实现定长环形缓冲区，容量按采样频率 × 10s 时间窗设计。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.2
 * </p>
 */
@Component
public class PhysiologicalDataBufferAdapter implements PhysiologicalDataBuffer {

    private static final Logger log = LoggerFactory.getLogger(PhysiologicalDataBufferAdapter.class);

    /**
     * 默认容量：100ms 采样率 × 10s = 100 槽
     */
    private static final int DEFAULT_CAPACITY = 100;

    private final int capacity;
    private final ArrayDeque<PhysiologicalSnapshot> buffer;

    public PhysiologicalDataBufferAdapter() {
        this(DEFAULT_CAPACITY);
    }

    public PhysiologicalDataBufferAdapter(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    /**
     * 添加快照到缓冲区。
     *
     * @param snapshot 生理快照
     */
    public void addSnapshot(PhysiologicalSnapshot snapshot) {
        if (buffer.size() >= capacity) {
            buffer.removeFirst();
        }
        buffer.addLast(snapshot);
        log.trace("Added physiological snapshot, buffer size: {}", buffer.size());
    }

    @Override
    public List<PhysiologicalSnapshot> getReadings(TripId tripId, TimeRange window) throws BufferException {
        if (buffer.isEmpty()) {
            throw new BufferException.WindowNotCoveredException("Buffer is empty");
        }

        Instant from = window.from();
        Instant to = window.to();

        // 检查窗口是否在缓冲范围内
        Instant oldest = buffer.peekFirst().timestamp();
        Instant newest = buffer.peekLast().timestamp();

        if (from.isBefore(oldest)) {
            throw new BufferException.WindowNotCoveredException(
                    String.format("Request window start %s is before buffer oldest %s", from, oldest));
        }

        List<PhysiologicalSnapshot> result = new ArrayList<>();
        for (PhysiologicalSnapshot snapshot : buffer) {
            Instant timestamp = snapshot.timestamp();
            if (!timestamp.isBefore(from) && !timestamp.isAfter(to)) {
                result.add(snapshot);
            }
        }

        log.debug("Retrieved {} physiological snapshots for window [{}, {}]", result.size(), from, to);
        return result;
    }

    /**
     * 获取当前缓冲区大小。
     */
    public int size() {
        return buffer.size();
    }

    /**
     * 清空缓冲区。
     */
    public void clear() {
        buffer.clear();
        log.debug("Physiological data buffer cleared");
    }
}
