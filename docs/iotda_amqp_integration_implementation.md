# IoTDA AMQP 数据接入实现报告

## 1. 概述

基于 `docs/iotda_amqp_integration_requirements.md` 完成 IoTDA AMQP 数据接入领域服务集成。`vehicle_safety` 设备每约 10 秒通过华为云 IoTDA 上报 23 个属性，经 AMQP 投递至 Spring Boot 服务，解析为领域对象后发布到 EventBus。

## 2. 架构

```
IoTDA AMQP DefaultQueue
        │
        ▼
IotdaAmqpConsumer (onMessage)
        │
        ▼
IotdaAmqpMessageRouter (JSON → 领域对象)
        │
        ├─ SensorReading × 4        → SensorDataCollected
        ├─ PhysiologicalSnapshot    → PhysiologicalDataUpdated
        ├─ VehicleStateSnapshot     → VehicleStateUpdated
        ├─ DrivingBehaviorCounters  → BehaviorCountersUpdated
        └─ SafetyAlertEvent         → SafetyAlertDetectedEvent（7 种告警规则）
                    │
                    ▼
              EventBus (EdgeEventBus / CloudEventBus)
```

## 3. 新增文件

| 文件 | 包 | 说明 |
|------|-----|------|
| `IotdaAmqpEnvelope.java` | `com.aiot.interfaces.amqp` | AMQP 消息 JSON → DTO（5 个 record：Envelope / NotifyData / Header / Body / ServiceData），`@JsonProperty` 映射 snake_case |
| `IotdaAmqpMessageRouter.java` | `com.aiot.interfaces.amqp` | 核心路由器：JSON 解析 → 属性提取 → 领域对象构建 → 事件发布 |
| `SensorDataCollected.java` | `com.aiot.domain.event` | 传感器数据采集事件（携带 List\<SensorReading\>） |
| `PhysiologicalDataUpdated.java` | `com.aiot.domain.event` | 生理体征更新事件（携带 PhysiologicalSnapshot） |
| `VehicleStateUpdated.java` | `com.aiot.domain.event` | 车辆状态更新事件（携带 VehicleStateSnapshot） |
| `BehaviorCountersUpdated.java` | `com.aiot.domain.event` | 驾驶行为计数器事件（携带 DrivingBehaviorCounters） |
| `SafetyAlertDetectedEvent.java` | `com.aiot.domain.event` | 安全告警检测事件（携带 alertType / riskLevel / lat / lon） |

## 4. 修改文件

| 文件 | 变更 |
|------|------|
| `SensorReading.java` | `SensorType` 新增 `ENVIRONMENT`，新增 `temp()` / `humi()` / `lux()` |
| `PhysiologicalSnapshot.java` | record 新增 `restingHr` 字段，保留向后兼容 compact constructor |
| `VehicleStateSnapshot.java` | record 新增 `gpsFix` 字段，保留向后兼容 compact constructor |
| `DrivingBehaviorCounters.java` | 新增 `sharpTurnCount` + `incrementSharpTurn()` + 6-arg `of()` 工厂 |
| `AlertType.java` | 新增 `SUDDEN_BRAKING` / `LOW_BATTERY` / `SYSTEM_RISK` |
| `IotdaAmqpConsumer.java` | 注入 `IotdaAmqpMessageRouter`，`onMessage` 无专用 handler 时自动路由 |
| `VehicleJpaRepository.java` | 新增 `findByTerminalSn(String)` |
| `VehicleRepository.java` | 新增 `findByTerminalSn(String)` |
| `VehicleRepositoryBridge.java` | 实现 `findByTerminalSn` 桥接方法 |

## 5. 属性→领域对象映射

### 5.1 SensorReading

| AMQP property | SensorType | values key |
|--------------|-----------|------------|
| `perclos`, `yawn`, `phone` | DMS_CAMERA | PERCLOS, YAWN, PHONE |
| `ax`, `ay`, `az`, `gx`, `gy`, `gz` | ACCELEROMETER | AX, AY, AZ, GX, GY, GZ |
| `temp`, `humi`, `lux` | ENVIRONMENT（新增） | TEMP, HUMI, LUX |
| `radar_human`, `radar_range_lo`, `radar_range_hi` | MILLIMETER_WAVE_RADAR | HUMAN, RANGE_LO, RANGE_HI |

仅当对应属性有值时创建 SensorReading。

### 5.2 PhysiologicalSnapshot

| AMQP property | 字段 |
|--------------|------|
| `hr` | heartRate |
| `spo2` | bloodOxygen |
| `resting_hr` | restingHr（新增字段） |

仅当 `hr` 或 `spo2` 有值时创建。

### 5.3 VehicleStateSnapshot

| AMQP property | 字段 |
|--------------|------|
| `lat` | latitude |
| `lon` | longitude |
| `gps_fix` | gpsFix（新增字段） |

仅当 `lat` 或 `lon` 有值时创建。

### 5.4 DrivingBehaviorCounters

| AMQP property | 字段 |
|--------------|------|
| `hard_brake` | suddenBrakingCount |
| `hard_accel` | suddenAccelerationCount |
| `sharp_turn` | sharpTurnCount（新增字段） |
| `perclos` | heavyFatigueCount（阈值累计） |
| `yawn` | distractionCount |

### 5.5 告警规则

| 条件 | AlertType | RiskLevel | 说明 |
|------|-----------|-----------|------|
| `risk` = 1 | SYSTEM_RISK | L1_HINT | IoTDA 系统风险等级 |
| `risk` = 2 | SYSTEM_RISK | L2_WARNING | |
| `risk` = 3 | SYSTEM_RISK | L3_CRITICAL | SOS 紧急级别 |
| `perclos` > 0.3 | FATIGUE | L2_WARNING | 眼睑闭合比例超阈值 |
| `yawn` > 5 | DISTRACTION | L2_WARNING | 哈欠计数超阈值 |
| `hard_brake` 增量 | SUDDEN_BRAKING | L2_WARNING | 急刹计数增加（上次值追踪） |
| `battery_mv` < 3300 | LOW_BATTERY | L2_WARNING | 电池低电压 |
| `pc_lvl` = 1 | FATIGUE | L1_HINT | PC 端预判轻度疲劳 |
| `pc_lvl` = 2 | FATIGUE | L2_WARNING | PC 端预判重度疲劳 |

## 6. 连接配置

`application-dev.yml`:

```yaml
aiot:
  amqp:
    host: 99d7c8973d.st1.iotda-app.cn-north-4.myhuaweicloud.com
    port: 5671
    access-key: XmtEC88F
    access-code: <secret>
    queue-name: DefaultQueue
```

## 7. 运行验证

```bash
cd code/server && mvn spring-boot:run
```

### 启动日志

```
启动 AMQP 消费者
AMQP 连接中: 99d7c8973d.st1.iotda-app.cn-north-4.myhuaweicloud.com
Connection ID:089688ad... connected to server: amqps://99d7c8973d.st1.iotda-app.cn-north-4.myhuaweicloud.com:5671
AMQP 已连接，订阅队列: DefaultQueue
AMQP 路由处理: deviceId=6a44f1047f2e6c302f80df85_vehicle_safety, properties count=23
AMQP 消息处理完成: deviceId=6a44f1047f2e6c302f80df85_vehicle_safety
```

### 消息频率

设备约每 10 秒上报一条属性消息，路由器持续处理所有到达消息。

## 8. 编译与测试

- **编译**: `mvn compile` 通过
- **EventBus 测试**: EdgeEventBusTest (10/10) + CloudEventBusTest (8/8) 全部通过
- **上下文加载**: Spring Boot ApplicationContext 正常启动

## 9. 后续扩展点

- 事件监听器：`SensorDataCollected` / `PhysiologicalDataUpdated` / `VehicleStateUpdated` / `BehaviorCountersUpdated` / `SafetyAlertDetectedEvent` 已发布到 EventBus，各子域服务可通过 `@EventListener` 或 `registerAsyncHandler` 订阅
- 设备 ID 解析：`VehicleRepository.findByTerminalSn()` 已就绪，可从 IoTDA device_id 解析为 Vehicle → Trip → Driver 链
- 云侧 outbox 模式：设置 `aiot.eventbus.mode=cloud` 可切换为 CloudEventBus（outbox 持久化 + 异步 relay）
