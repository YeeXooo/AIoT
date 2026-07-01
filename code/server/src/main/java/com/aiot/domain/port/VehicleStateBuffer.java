package com.aiot.domain.port;

import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.shared.TripId;

import java.util.List;

/**
 * 车辆状态滚动缓冲端口。
 * <p>
 * 事故前 30 秒车辆状态的 ring buffer，由基础设施层实现。
 * EmergencyResponseService 在碰撞时刻通过此端口回取事故前 30s 的 VehicleStateSnapshot。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 14
 * </p>
 */
public interface VehicleStateBuffer {

    /**
     * 获取指定时间窗内的车辆状态快照。
     *
     * @param tripId 当前活跃行程标识
     * @param window 回取时间窗（事故前 30s）
     * @return 时间窗内按时序排列的快照列表
     * @throws BufferException 缓冲异常
     */
    List<VehicleStateSnapshot> getSnapshots(TripId tripId, TimeRange window) throws BufferException;
}
