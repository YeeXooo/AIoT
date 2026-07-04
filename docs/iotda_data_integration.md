# IoTDA 物模型 & AMQP 数据接入文档

## 1. 数据流向

```
嵌入式设备 (WS63) ──MQTT──> 华为云 IoTDA ──数据转发──> AMQP 队列 (DefaultQueue)
                                                              │
                                              Spring Boot 服务端 (IotdaAmqpConsumer)
```

## 2. 产品物模型 (Product Profile)

Service ID: `VehicleSafety`（车载安全检测系统）

### 2.1 全部属性定义

| # | property_name | 描述 | data_type |
|---|--------------|------|-----------|
| 1 | `temp` | 温度 | string |
| 2 | `humi` | 湿度 | string |
| 3 | `lux` | 光照强度 | string |
| 4 | `ax` | 加速度X（前后） | string |
| 5 | `ay` | 加速度Y（左右） | string |
| 6 | `az` | 加速度Z（上下，含重力） | string |
| 7 | `gx` | 角速度X（横滚） | string |
| 8 | `gy` | 角速度Y（俯仰） | string |
| 9 | `gz` | 角速度Z（偏航，BR-05 急转弯判定用） | string |
| 10 | `lat` | GPS 纬度，正为北纬 | string |
| 11 | `lon` | GPS 经度，正为东经 | string |
| 12 | `gps_fix` | GPS 定位状态（0 无效，1 已定位） | string |
| 13 | `hr` | 实时心率 | string |
| 14 | `spo2` | 血氧饱和度 | string |
| 15 | `resting_hr` | 静息心率基准（BR-03 路怒判定用） | string |
| 16 | `battery_mv` | 电池电压，低于 3300mV 告警 | string |
| 17 | `battery_pct` | 电量百分比 | string |
| 18 | `perclos` | 眼睑闭合比例 | string |
| 19 | `yawn` | 哈欠计数 | string |
| 20 | `phone` | 接打电话标记 | string |
| 21 | `pc_lvl` | PC 端预判疲劳等级（0 清醒，1 轻度，2 重度） | string |
| 22 | `risk` | 风险等级（0=NONE, 1=L1, 2=L2, 3=L3） | string |
| 23 | `score` | 驾驶评分 | string |
| 24 | `hard_brake` | 急刹计数 | string |
| 25 | `hard_accel` | 急加速计数 | string |
| 26 | `sharp_turn` | 急转弯计数 | string |
| 27 | `radar_human` | 人体存在 | string |
| 28 | `radar_range_lo` | 检测下边界 | string |
| 29 | `radar_range_hi` | 检测上边界 | string |

> 注：模型定义中 `data_type` 均为 `string`，但实际 AMQP 消息体中值为 JSON number 类型。

## 3. AMQP 消息格式

### 3.1 完整 JSON 结构

```json
{
  "resource": "device.property",
  "event": "report",
  "event_time": "20260703T183519Z",
  "event_time_ms": "2026-07-03T18:35:19.480Z",
  "request_id": "65fb87b2-70dd-4f34-adae-c5e668fd9b2e",
  "notify_data": {
    "header": {
      "app_id": "b95d5f94678e4505927f7bbe13d345cf",
      "device_id": "6a44f1047f2e6c302f80df85_vehicle_safety",
      "node_id": "vehicle_safety",
      "product_id": "6a44f1047f2e6c302f80df85",
      "gateway_id": "6a44f1047f2e6c302f80df85_vehicle_safety"
    },
    "body": {
      "services": [{
        "service_id": "VehicleSafety",
        "event_time": "20260703T183519Z",
        "properties": {
          "temp": 24.0,
          "humi": 60.0,
          "lux": 65,
          "lat": 41.8028,
          "lon": 123.5497,
          "gps_fix": 1,
          "ax": 0.2,
          "ay": 0.0,
          "az": 9.8,
          "gx": 0.8,
          "gy": 0.7,
          "gz": 2.2,
          "perclos": 0.09,
          "yawn": 0,
          "phone": 0,
          "pc_lvl": 0,
          "risk": 0,
          "score": -1,
          "hard_brake": 24,
          "hard_accel": 59,
          "sharp_turn": 32,
          "battery_mv": 3886,
          "battery_pct": 82
        }
      }]
    }
  }
}
```

### 3.2 关键字段说明

| 字段路径 | 说明 |
|---------|------|
| `resource` | `device.property` — 设备属性上报 |
| `event` | `report` — 上报事件 |
| `notify_data.header.device_id` | 设备唯一标识 |
| `notify_data.header.product_id` | 产品 ID |
| `notify_data.body.services[0].service_id` | `VehicleSafety` |
| `notify_data.body.services[0].properties` | 实际传感器数据 |

## 4. AMQP 连接参数

| 参数 | 值 |
|------|-----|
| 接入地址 | `99d7c8973d.st1.iotda-app.cn-north-4.myhuaweicloud.com` |
| 端口 | `5671` |
| 队列名 | `DefaultQueue` |
| SASL 机制 | `PLAIN` |
| Username 格式 | `accessKey={access_key}\|timestamp={System.currentTimeMillis()}` |
| Password | `{access_code}` 明文 |

配置位置: `application-dev.yml` → `aiot.amqp.*`

## 5. 与工程领域模型对照

### 5.1 已有映射

| IoTDA 属性 | 工程领域类 | 字段 |
|-----------|-----------|------|
| `perclos` | `SensorReading` | `values["PERCLOS"]` |
| `yawn` | `SensorReading` | `values["YAWN"]` |
| `phone` | `SensorReading` | `values["PHONE"]` |
| `ax`, `ay`, `az` | `AccelerationPayload` | `x`, `y`, `z` |
| `lat`, `lon` | `GeoLocation` | `latitude`, `longitude` |
| `hr`, `spo2`, `resting_hr` | `PhysiologicalSnapshot` | `heartRate`, `bloodOxygen`, — |
| `hard_brake`, `hard_accel`, `sharp_turn` | `DrivingBehaviorCounters` | `suddenBrakingCount`, `suddenAccelerationCount`, — |
| `risk` | `RiskLevel` 枚举 | L1/L2/L3 |
| `pc_lvl` | `DetectionWindow` / 疲劳判定 | — |

### 5.2 未映射（需新增，暂为冗余）

| IoTDA 属性 | 建议新增的 POJO |
|-----------|----------------|
| `gx`, `gy`, `gz` | `GyroscopePayload` (x/y/z) |
| `temp`, `humi`, `lux` | `EnvironmentPayload` (temperature/humidity/lux) |
| `battery_mv`, `battery_pct` | `BatteryPayload` (voltage/percentage) |
| `radar_human`, `radar_range_lo`, `radar_range_hi` | `RadarPayload` |
| `score` | 合并到 `TripScore` 领域对象 |
| `gps_fix` | 合并到 `GeoLocation` 或 `VehicleStateSnapshot` |

## 6. 服务端代码入口

| 文件 | 职责 |
|------|------|
| `interfaces/amqp/IotdaAmqpProperties.java` | AMQP 连接配置（`aiot.amqp.*` 绑定 + 动态 username 拼接） |
| `interfaces/amqp/IotdaAmqpConsumer.java` | AMQP 消费者，连接队列 + 消息监听 |
| `interfaces/amqp/IotdaAmqpBootstrap.java` | Spring Boot 启动/关闭钩子 |
| `application-dev.yml` | 开发环境凭证配置 |

### 6.1 启动方式

```bash
cd code/server
mvn spring-boot:run
```

服务启动后自动连接 AMQP 队列，消息实时打印到控制台日志。

## 7. 数据上报频率

嵌入式设备约每 **10 秒**上报一条属性消息。数据包含 GPS 轨迹、加速度/角速度、疲劳检测、驾驶行为计数等全部传感器融合结果。
