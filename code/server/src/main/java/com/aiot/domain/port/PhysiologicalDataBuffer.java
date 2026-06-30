package com.aiot.domain.port;

import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.shared.TripId;

import java.util.List;

/**
 * 生理数据滚动缓冲端口。
 * <p>
 * 碰撞前后 ≥10 秒的生理数据缓冲，由基础设施层实现。
 * EmergencyResponseService 在碰撞时刻通过此端口回取失能判定所需的生理读数。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 21
 * </p>
 */
public interface PhysiologicalDataBuffer {

    /**
     * 获取指定时间窗内的生理数据读数。
     *
     * @param tripId 当前活跃行程标识
     * @param window 回取时间窗（碰撞前至碰撞后 ≥10s）
     * @return 时间窗内按时序排列的生理快照列表
     * @throws BufferException 缓冲异常
     */
    List<PhysiologicalSnapshot> getReadings(TripId tripId, TimeRange window)
            throws BufferException;
}
