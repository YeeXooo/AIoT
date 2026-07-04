# IoTDA AMQP 数据接入 → 领域服务集成需求

## 1. 现状

`IotdaAmqpConsumer` 已通过 AMQP 成功接收 `vehicle_safety` 设备的上报数据，当前仅在日志中打印原始 JSON，未与领域层对接。

## 2. 目标

将 AMQP 接收的 IoTDA 属性上报 JSON 解析为领域对象，注入已有的业务管线：

```
AMQP DefaultQueue → JSON 解析 → 领域对象 → EventBus → 各子域服务
```

## 3. JSON → 领域对象映射

每一条 AMQP 消息包含 `VehicleSafety` service 的 `properties`，需拆解后映射到以下领域对象：

### 3.1 传感器原始数据 → `SensorReading`

对每个有值的 sensor 创建一条 `SensorReading`：

| AMQP property | SensorReading.sensorType | SensorReading.values key |
|--------------|-------------------------|--------------------------|
| `perclos` | `DMS_CAMERA` | `PERCLOS` |
| `yawn` | `DMS_CAMERA` | `YAWN` |
| `phone` | `DMS_CAMERA` | `PHONE` |
| `ax`, `ay`, `az` | `ACCELEROMETER` | `AX`, `AY`, `AZ` |
| `gx`, `gy`, `gz` | `ACCELEROMETER` | `GX`, `GY`, `GZ` |
| `temp`, `humi`, `lux` | 新增 `ENVIRONMENT` | `TEMP`, `HUMI`, `LUX` |
| `radar_human`, `radar_range_lo`, `radar_range_hi` | `MILLIMETER_WAVE_RADAR` | `HUMAN`, `RANGE_LO`, `RANGE_HI` |

### 3.2 生理体征 → `PhysiologicalSnapshot`

| AMQP property | PhysiologicalSnapshot 字段 |
|--------------|---------------------------|
| `hr` | `heartRate` |
| `spo2` | `bloodOxygen` |
| `resting_hr` | （新增字段 `restingHr`） |

仅当 `hr` 或 `spo2` 有值时创建。

### 3.3 车辆状态 → `VehicleStateSnapshot`

| AMQP property | VehicleStateSnapshot 字段 |
|--------------|--------------------------|
| `lat` | `latitude` |
| `lon` | `longitude` |
| `gps_fix` | （新增字段 `gpsFix`） |

### 3.4 驾驶行为 → `DrivingBehaviorCounters`

| AMQP property | DrivingBehaviorCounters 字段 |
|--------------|------------------------------|
| `hard_brake` | `suddenBrakingCount` |
| `hard_accel` | `suddenAccelerationCount` |
| `sharp_turn` | （新增字段 `sharpTurnCount`） |
| `perclos` | `heavyFatigueCount`（累计阈值判定） |
| `yawn` | `distractionCount`（累计） |

### 3.5 安全告警 → `SafetyAlertEvent`

| AMQP property | 告警规则 |
|--------------|---------|
| `risk` = 1 | L1 告警（提示级别） |
| `risk` = 2 | L2 告警（警告级别） |
| `risk` = 3 | L3 告警（紧急级别，触发 SOS） |
| `perclos` > 阈值 | 疲劳告警 |
| `yawn` > 阈值 | 分神告警 |
| `hard_brake` 增量 | 急刹事件 |
| `battery_mv` < 3300 | 电池低电压告警 |

### 3.6 边缘判定 → 直接消费

| AMQP property | 说明 |
|--------------|------|
| `pc_lvl` | PC 端疲劳预判（0/1/2），可跳过引擎直接使用 |
| `score` | 驾驶评分，写入行程评分 |

## 4. 消息解析 DTO（需新增）

```java
// 外层信封
class IotdaAmqpEnvelope {
    String resource;       // "device.property"
    String event;          // "report"
    String eventTime;      // ISO 8601
    NotifyData notifyData;
}

class NotifyData {
    Header header;
    Body body;
}

class Header {
    String deviceId;       // "6a44f1047f2e6c302f80df85_vehicle_safety"
    String nodeId;         // "vehicle_safety"
    String productId;      // "6a44f1047f2e6c302f80df85"
}

class Body {
    List<ServiceData> services;
}

class ServiceData {
    String serviceId;      // "VehicleSafety"
    String eventTime;
    Map<String, Object> properties;  // 29 个属性，值为 JSON number/string
}
```

## 5. 改造范围

### 5.1 `IotdaAmqpConsumer` 改造

在 `onMessage()` 回调中增加：

1. JSON 反序列化 → `IotdaAmqpEnvelope`
2. 调用新组件 `IotdaAmqpMessageRouter.route(envelope)` 分发

### 5.2 新增 `IotdaAmqpMessageRouter`

```
IotdaAmqpEnvelope → {deviceId, properties}
    ├─ SensorReading × N  → EventBus.publish(SensorDataCollected)
    ├─ PhysiologicalSnapshot × 1 → EventBus.publish(PhysiologicalDataUpdated)
    ├─ VehicleStateSnapshot × 1   → EventBus.publish(VehicleStateUpdated)
    ├─ DrivingBehaviorCounters     → EventBus.publish(BehaviorCountersUpdated)
    └─ SafetyAlertEvent（条件触发）→ EventBus.publish(AlertDetected)
```

### 5.3 各子域服务已有 EventBus 监听

| 事件类型 | 消费方 | 行为 |
|---------|--------|------|
| `SensorDataCollected` | 判定引擎 (SafetyJudgmentEngine) | 多传感器融合 + 风险评估 |
| `PhysiologicalDataUpdated` | 健康监控服务 | 心率/血氧异常检测 |
| `VehicleStateUpdated` | 车辆状态服务 | GPS 轨迹记录、车门/车窗状态 |
| `BehaviorCountersUpdated` | 驾驶行为服务 | 急刹/急加速/急转弯累计 |
| `AlertDetected` | 告警分发服务 | → 家属 APP 推送 / 车队大屏 |

## 6. 不做的事（本期范围外）

- 不新增 IoTDA REST API 调用（设备管理、影子下发）
- 不改造 IoTDA 控制台已有配置（转发规则、产品模型）
- 不新增数据库表或 Flyway 迁移（除非现有表不足）
- 不新增 MQTT 下行指令通路（云端反控设备暂不实施）

## 7. 验证方式

1. 启动 `spring-boot:run`，确认 `IotdaAmqpConsumer` 连接成功
2. 观察日志中不再打印原始 JSON，改为打印路由后的领域事件摘要
3. 运行现有 `AlertDetected` 相关单元测试确保事件格式兼容
4. （可选）模拟 `risk=3` 报文验证 SOS 告警链路
