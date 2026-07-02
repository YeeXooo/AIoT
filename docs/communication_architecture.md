# AIoT 组件通信架构与数据载荷总览

> 本文档概括系统各组件的通信方式及传输内容，基于 `docs/ood_interface.md`、`code/perception/proto/dms_perception.proto` 及前端源码整理。

---

## 一、通信通道总图

```
┌────────────────────────────────────────────────────────────────────────┐
│                          边缘侧 (车载终端)                                │
│                                                                        │
│  [Python YOLO Sidecar] ──gRPC──> [Java 边缘服务] ──进程内──> [车机 HMI] │
│       port 50051                                                       │
│                                                                        │
│  [传感器 / CAN 总线] ──本地驱动──> [Java 边缘服务]                        │
│                                                                        │
│  [Java 边缘服务] ──── MQTT (TLS 1.2+) ────> [华为云 IoTDA]               │
└────────────────────────────────────────────────────────────────────────┘
                                            │
                                    MQTT (TLS 1.2+)
                                            │
┌────────────────────────────────────────────────────────────────────────┐
│                          云 端                                         │
│                                                                        │
│  [华为云 IoTDA] ──MQTT──> [Spring Boot 云端服务]                         │
│                                                                        │
│  [Spring Boot 云端服务]                                                 │
│    ├── EdgeEventBus (同步进程内)                                         │
│    ├── CloudEventBus (Outbox → 异步轮询 5s)                              │
│    ├── JPA ──> [PostgreSQL / KingBase]                                 │
│    └── 8 个 Port Adapter (stub) → 外部系统                               │
│                                                                        │
│  [Spring Boot 云端服务]                                                  │
│    ├── REST (HTTPS) ──────────────> [HarmonyOS 家属 APP]                │
│    ├── WebSocket (WSS) ───────────> [HarmonyOS 家属 APP]                │
│    ├── WebSocket (WSS) ───────────> [车队大屏]                           │
│    └── REST (HTTPS) ──────────────> [救援机构控制台]                     │
│                                                                        │
│  [SparkRTC] 音视频流 ──端到端 P2P──> [家属 APP ⟷ 车机端]                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 二、各通道传输内容详解

### 2.1 Python 感知模块 → Java 边缘服务（gRPC 双向流）

| 属性 | 值 |
|------|-----|
| 协议 | gRPC over plaintext TCP |
| 契约文件 | `code/perception/proto/dms_perception.proto` |
| 端口 | 50051 |
| 模式切换 | `aiot.perception.dms.mode` = `mock`(本地模拟) / `yolo`(真实Python) |

#### Java→Python 的内容（ControlSignal，请求流）

| 信号 | 字段 | 说明 |
|------|------|------|
| FrameRateAdjust | `target_fps: double` | 调整帧率 |
| Shutdown | — | 关闭 sidecar |

#### Python→Java 的内容（DmsFeatureFrame，响应流）

> 每帧均为纯数值特征，原始图像不出 Python 进程内存（BR-04 隐私边界）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp_ms` | int64 | 采集时间戳（毫秒 UTC） |
| `sensor_id` | string | 摄像头标识 |
| `perclos` | double [0,1] | PERCLOS 眼睑闭合比例 |
| `yawn_freq` | double | 打哈欠频率（次/10s） |
| `head_nod_freq` | double | 点头频率（次/10s） |
| `gaze_deviation_cumulative` | double | 视线偏离累计时长（滑动窗口，秒） |
| `hands_off_wheel` | double [0,1] | 双手脱离方向盘置信度 |
| `confidence` | double [0,1] | 整体帧特征可信度 |
| `phone_detected` | double [0,1] | 手机检出置信度（-1=未启用） |
| `smoking_detected` | double [0,1] | 香烟检出置信度（-1=未启用） |
| `frame_seq` | uint64 | 帧序列号（供 Java 侧检测丢帧） |

#### 健康检查（Health RPC）

| 方向 | 字段 | 说明 |
|------|------|------|
| Java→Python | — | 空请求 |
| Python→Java | `alive: bool` | 是否存活 |
| Python→Java | `start_time_ms: int64` | 启动时间 |
| Python→Java | `current_fps: double` | 当前帧率 |
| Python→Java | `last_frame_ms: int64` | 最后帧时间 |

---

### 2.2 边缘 → 华为云 IoTDA（MQTT，已设计/未实现）

**认证**：X.509 证书（TLS 1.2+），设备密钥作为备选方案。

#### 上行 Topic（设备 → 云）

| Topic | QoS | 频率 | 载荷 |
|-------|:---:|------|------|
| `{deviceId}/sensor/{sensorType}/up` | 1 | DMS ≥10Hz，生理 ≥1Hz，语音 ≥1Hz | **SensorReading** — `{ timestamp, sensorId, sensorType, values }`。sensorType: `DMS_CAMERA`(PERCLOS/yawnFreq/headNodFreq/gazeDeviation/handsOffWheel)、`MILLIMETER_WAVE_RADAR`(microMotion/breathingRate)、`ACCELEROMETER`(accelX/Y/Z/collisionImpact/hardBraking/hardAcceleration)、`PHYSIOLOGICAL_MONITOR`(heartRate/spo2/hrResting/rrInterval)、`MICROPHONE`(spl_dB/keywordsDetected/speechRate)、`REAR_IR_CAMERA`(frameRef) |
| `{deviceId}/trip/status/up` | 1 | 事件驱动 | **TripStatusEvent** — `{ tripId, driverId, vehicleId, eventType(TRIP_STARTED/TRIP_COMPLETED), occurredAt, gpsLocation? }` |
| `{deviceId}/alert/up` | 1 | 事件驱动 | **SafetyAlertEvent** — `{ alertId, alertType(FATIGUE/DISTRACTION/ROAD_RAGE/LIFE_DETECTION/COLLISION_DISABILITY/PERFORMANCE_WARNING), riskLevel(L1/L2/L3), occurredAt, resolvedAt?, tripId, gpsLocation?, featureSnapshot }` |
| `{deviceId}/physiological/snapshot/up` | 1 | ≥1Hz | **PhysiologicalSnapshot** — `{ driverId, timestamp, heartRate, spo2, emotionIndex(0-1), hrResting, rrInterval }` |
| `{deviceId}/vehicle/state/up` | 1 | ≥1Hz | **VehicleStateSnapshot** — `{ vehicleId, timestamp, speed(km/h), doorLockState(LOCKED/UNLOCKED), acceleration{x,y,z}, fuelLevel, odometer, windowStatuses[] }` |
| `{deviceId}/status/heartbeat/up` | 0 | 30s | **Heartbeat** — `{ deviceId, timestamp, sequenceNumber, sensorSelfCheck{overallStatus,failedSensorCount,lastSelfCheckAt}, systemMetrics{cpuUsage,memoryUsage,storageAvailable,uptimeSeconds} }` |
| `{deviceId}/sensor/fault/up` | 1 | 事件驱动（3s SLA） | **SensorFailureEvent** — `{ deviceId, failedSensors[{sensorId,sensorType,failureType,errorCode}], occurredAt }` |
| `{deviceId}/sensor/occlusion/up` | 1 | 事件驱动 | **CameraOcclusionEvent** — `{ deviceId, sensorId, eventType(DETECTED/REMOVED), occlusionType?(PHYSICAL_COVER/SOFTWARE_DISABLE/DRIVER_DEACTIVATED), occurredAt }` |
| `{deviceId}/driver/override/up` | 1 | 事件驱动 | **OverrideSignal** — `{ driverId, tripId, overrideType(STEER/BRAKE/ACCELERATE), occurredAt, gpsLocation? }` |
| `{deviceId}/trip/score/up` | 1 | 行程结束时 | **TripScore** — `{ tripId, driverId, score(0-100), penaltyItems[{category,penaltyScore,violationCount,description}], occurredAt }` |
| `{deviceId}/voice/evidence/up` | 1 | 行程结束时/存储压力时 | **RoadRageVoiceRecord** — `{ recordId, tripId, driverId, encryptedPayload(AES-256-GCM Base64), encryptionMetadata{algorithm,keyId,iv,authTag}, durationSeconds, occurredAt }` |
| `{deviceId}/cmd/{commandId}/ack` | 1 | 指令完成后 | **CommandAck** — `{ commandId, result(SUCCESS/TIMEOUT/FAILED/PARTIAL), failureReason?, completedAt, detail? }` |

#### 下行 Topic（云 → 设备）

| Topic | QoS | 载荷 |
|-------|:---:|------|
| `{deviceId}/cmd/intervention/down` | 1 | **InterventionInstruction** — `{ commandId, issuedAt, interventions[{interventionId, interventionType(AMBIENT_LIGHT_COLOR/VOICE_BROADCAST/SEAT_VIBRATION/HAZARD_LIGHTS/AIR_CONDITIONING/AUDIO_PLAYBACK/CAN_DECELERATION_REQUEST/NAVIGATE_DECELERATION/NAVIGATE_TO_SHOULDER/ALERT), targetDevice, parameters, priority(1-3)}] }` |
| `{deviceId}/cmd/window/down` | 1 | **WindowControl** — `{ commandId, driverId, windowOperation(OPEN/CLOSE/PARTIAL_OPEN), windowPosition(FRONT_LEFT/FRONT_RIGHT/REAR_LEFT/REAR_RIGHT), issuedAt, secondaryAuthToken }` |
| `{deviceId}/cmd/door/unlock/down` | 1 | **DoorUnlock** — `{ commandId, rescueTokenId, targetVehicleId, issuedAt, rescueTokenSignature }` |
| `{deviceId}/cmd/ota/down` | 1 | **OTAPackage** — `{ commandId, taskId, newVersion{major,minor,patch,buildNumber}, chunkIndex, totalChunks, chunkOffset?, chunkSize?, payload(Base64固件分片), checksum(SHA-256), issuedAt }` |
| `{deviceId}/cmd/ota/rollback/down` | 1 | **OTARollback** — `{ commandId, vehicleId, targetVersion, reason, issuedAt }` |
| `{deviceId}/cmd/media/join/down` | 1 | **SparkRTCJoin** — `{ commandId, sparkRTCRoomId, sparkRTCJoinToken, issuedAt }` |

#### 推送 Topic（云 → APP/大屏）

| Topic | QoS | 目标 | 载荷 |
|-------|:---:|------|------|
| `family/{accountId}/alert/push` | 1 | 家属 APP | **AlertTriggeredEvent** — `{ alertId, alertType, riskLevel, driverId, vehicleId, occurredAt, tripId, gpsLocation? }` ≤10s（活体遗留）或秒级 |
| `family/{accountId}/status/push` | 1 | 家属 APP | **DriverStatusSnapshot** — `{ driverId, vehicleId, timestamp, activeAlertLevels, gpsLocation?, speed?, tripStatus, physiologicalSummary{heartRate,spo2,emotionIndex}?, windowStatus?[] }` ≥1Hz |
| `family/{accountId}/access/granted` | 1 | 家属 APP | **FamilyAccessGrantedEvent** — `{ driverId, sessionToken, sparkRTCRoomId, sparkRTCJoinToken, reason(REGULAR_60S/EMERGENCY_ACTIVATION/OCCLUSION_RECOVERY) }` |
| `family/{accountId}/access/revoked` | 1 | 家属 APP | **FamilyAccessRevokedEvent** — `{ driverId, reason(RISK_DECLINED/CAMERA_OCCLUDED/DRIVER_DEACTIVATED) }` |
| `fleet/{fleetId}/alert/push` | 1 | 车队大屏 | **L3AlertEvent** — `{ fleetId, driverId, vehicleId, alertType, riskLevel(固定L3), occurredAt, gpsLocation? }` 秒级 |
| `fleet/{fleetId}/performance-warning/push` | 1 | 车队大屏 | **PerformanceWarningEvent** — `{ driverId, driverName, fleetId, score, scorePeriod(trip/weekly/monthly/quarterly), primaryPenaltyItems[], occurredAt }` |
| `app/{accountId}/rescue/confirm` | 1 | 家属 APP | **SOSConfirm** — `{ rescueReportId, status(CONFIRMED/PENDING_RETRY/MANUAL_ESCALATION), confirmedAt?, message? }` |

---

### 2.3 云后端内部事件总线

#### EdgeEventBus（边缘侧，同步进程内）

安全关键控制环路，所有 handler 在 `publish()` 返回前执行完毕。用于：
- Risk → Alert → Intervention 级联触发
- CameraOcclusion → 家属权限撤销
- EmergencyActivation → 家属自动接入

#### CloudEventBus（云端，Outbox + 异步轮询）

写入 `domain_event_outbox` 表，`OutboxRelayer` 每 5s 轮询发布。支持指数退避重试（最多 10 次），死信进入 `domain_event_dlq`。

#### 18 个领域事件

| 事件类 | 聚合根 | 携带数据 |
|--------|--------|----------|
| `AlertTriggeredEvent` | Trip | `alertId, alertType, riskLevel, driverId, tripId, occurredAt, resolvedAt?, gpsLocation?` |
| `CameraOcclusionDetectedEvent` | Vehicle | `deviceId, sensorId, occlusionType` |
| `CameraOcclusionRemovedEvent` | Vehicle | `deviceId, sensorId` |
| `DriverDeactivatedEvent` | Driver | `driverId` |
| `DriverScoreUpdatedEvent` | Driver | `driverId, score, period` |
| `EmergencyActivatedEvent` | Trip | `tripId, driverId, triggerType, occurredAt` |
| `FamilyAccessGrantedEvent` | Trip | `driverId, familyAccountId, reason` |
| `FamilyAccessRevokedEvent` | Trip | `driverId, familyAccountId, reason` |
| `FamilyManualRescueRequestedEvent` | Driver | `familyAccountId, driverId` |
| `LifeDetectedEvent` | Vehicle | `vehicleId, driverId, occurredAt` |
| `OTAUpgradeCompletedEvent` | Vehicle | `vehicleId, newVersion, completedAt` |
| `OTAUpgradeFailedEvent` | Vehicle | `vehicleId, errorCode, failedAt` |
| `PerformanceWarningEvent` | Driver | `driverId, score, penaltyItems` |
| `RiskDeterminedEvent` | Trip | `tripId, driverId, alertType, riskLevel, timestamp` |
| `RiskResolvedEvent` | Trip | `tripId, driverId, alertType` |
| `SensorFailureEvent` | Vehicle | `deviceId, sensorId, sensorType, failureType` |
| `TripScoredEvent` | Trip | `tripId, driverId, score, penaltyItems` |
| `VehicleIgnitionOffLockedEvent` | Vehicle | `vehicleId, driverId, occurredAt` |

---

### 2.4 前端 → 后端（REST API）

**协议**：HTTPS，JWT Bearer `Authorization` 头  
**基础路径**：`/api/v1`  
**JWT 载荷**：`{ sub(accountId), role(FAMILY/MANAGER/RESCUE), iat, exp }`

#### S1 风险监测

| 端点 | 内容 |
|------|------|
| `GET /drivers/{driverId}/risk-status` | 响应：`{ hasActiveTrip, activeAlerts[{alertType,riskLevel}], derivedStatusColor(GREEN/YELLOW/RED) }` |
| `GET /drivers/{driverId}/alerts` | 查询：`alertType, riskLevel, startTime, endTime, page, size`。响应：`{ alerts[{alertId,alertType,riskLevel,occurredAt,resolvedAt?,tripId,gpsLocation?}], totalCount }` |

#### S2 干预执行

| 端点 | 内容 |
|------|------|
| `GET /trips/{tripId}/interventions/active` | 响应：`{ activeInterventions[{interventionId, interventionType(灯光/语音/震动...), targetDevice, parameters, priority, issuedAt}] }` |
| `GET /trips/{tripId}/interventions/history` | 同上 + 分页 |

#### S3 远程监护

| 端点 | 请求 | 响应 |
|------|------|------|
| `POST /guardianship/media-session` | `{ familyAccountId, driverId, sessionType(AUDIO/VIDEO), secondaryAuthToken }` | `{ sessionHandle, sessionToken, sparkRTCRoomId, sparkRTCJoinToken }` |
| `DELETE /guardianship/media-session/{sessionHandle}` | — | 204 |
| `PUT /guardianship/notification-preference` | `{ familyAccountId, driverId, preferredRiskLevels[] }` | 204 |
| `POST /guardianship/manual-rescue` | `{ familyAccountId, driverId, secondaryAuthToken }` | `{ rescueRequestId, rescueReportId, status }` |
| `POST /guardianship/window-control` | `{ familyAccountId, driverId, windowOperation, windowPosition, secondaryAuthToken }` | 202 |
| `GET /vehicles/{vehicleId}/windows` | — | `{ windowStatuses[{windowPosition, state, lastOperation?, lastOperationResult?, updatedAt}] }` |
| `GET /guardianship/{driverId}/permissions` | — | `{ familyAccountId, driverId, permissions[{permissionType, granted, grantedAt, expiresAt?}], careRelationship{status, establishedAt} }` |
| `POST /sparkrtc/token` | `{ roomId, userId, role(subscriber) }` | `{ token, expiresAt }` |

#### S4 车队管理

| 端点 | 请求 | 响应 |
|------|------|------|
| `GET /fleet/{fleetId}/fatigue-distribution` | `startTime, endTime` | `{ distribution{L1/L2/L3}, heatmapData[{lat,lng,riskIntensity}], dataFreshness, generatedAt }` |
| `GET /fleet/{fleetId}/offline-vehicles` | — | `{ offlineVehicles[{vehicleId,licensePlate,driverId,driverName,offlineReason,offlineSince,lastHeartbeat}] }` |
| `GET /fleet/{fleetId}/trajectory` | `vehicleId, driverId, startTime, endTime, page, size` | `{ trajectoryPoints[{timestamp,latitude,longitude,speed}], totalCount, dataConsistency }` |
| `GET /fleet/{fleetId}/high-risk-drivers` | `riskLevel, page, size` | `{ drivers[{driverId,driverName,compositeRiskScore,latestTripSummary,primaryPenaltyItems[]}], totalCount }` |
| `POST /fleet/reports` | `{ driverId, timeRange{start,end}, reportType }` | `{ reportId, reportData{overallScore, subScores, riskDistribution, penaltyBreakdown, totalMileage}, downloadUrl, isEmpty }` |
| `GET /fleet/reports/{reportId}/download` | `format` (pdf/xlsx) | 二进制文件流 |
| `POST /fleet/performance-warning-subscription` | `{ adminId, fleetId }` | `{ subscriptionId }` |
| `DELETE /fleet/performance-warning-subscription/{subscriptionId}` | — | 204 |

#### S5 应急救援

| 端点 | 内容 |
|------|------|
| `POST /emergency/sos-confirm` | `{ rescueReportId, ackToken }` → 204 |
| `POST /emergency/rescue-tokens` | `{ rescueReportId, authorizedOperations[], validityDurationSeconds }` → `{ rescueToken{tokenId, targetVehicleId, authorizedOperations, expiresAt, signature} }` |
| `POST /emergency/rescue-tokens/verify` | `{ rescueToken, requestedOperation, targetVehicleId }` → `{ result(VALID) }` |
| `GET /emergency/rescue-history` | 查询：`driverId, vehicleId, startTime, endTime, page, size`。响应：`{ rescueRecords[{rescueReportId,driverId,licensePlate,triggerType,status,occurredAt}], totalCount }` |

#### S6 OTA 管理

| 端点 | 内容 |
|------|------|
| `POST /ota/upgrade-tasks` | `{ targetVehicleIds[], targetVersion{major,minor,patch,buildNumber}, upgradeOptions{batchStrategy,scheduledWindow,forceUpgrade}, idempotencyKey }` → `{ createdTaskIds[], skippedVehicles[] }` |
| `GET /ota/upgrade-progress` | `vehicleIds` → `{ progressEntries[{vehicleId, currentStage, progressPercent, estimatedRemainingSeconds}] }` |
| `POST /ota/rollback` | `{ vehicleId, reason }` → `{ vehicleId, newStatus(ROLLING_BACK/ROLLED_BACK) }` |
| `GET /ota/upgrade-history/{vehicleId}` | → `{ entries[{taskId, oldVersion, newVersion, duration, finalStatus}], totalCount }` |
| `DELETE /ota/upgrade-tasks/{taskId}` | → `{ taskId, previousStatus, cancelledAt }` |

#### Auth 认证

| 端点 | 内容 |
|------|------|
| `POST /auth/login` | `{ authMethod(PASSWORD/SMS_CODE), credential, secret }` → `{ accessToken, refreshToken, tokenType, expiresIn, accountId, role }` |
| `POST /auth/refresh` | `{ refreshToken }` → `{ accessToken, refreshToken, tokenType, expiresIn }` |
| `POST /auth/secondary-verify` | `{ accountId, method(OTP/BIOMETRIC), otp? }` → `{ secondaryAuthToken, expiresAt(5min有效期) }` |

---

### 2.5 前端 → 后端（WebSocket）

#### 家属 APP（`wss://.../ws/guardianship?token=<JWT>`）

**上行（APP → 云端）**：

| 消息 type | Payload | 说明 |
|-----------|---------|------|
| `subscribe_status` | `{ driverId }` | 订阅实时状态（≥1Hz） |
| `unsubscribe_status` | `{ subscriptionId }` | 取消订阅 |
| `pong` | `{}` | 心跳响应 |
| `request_media` | `{ familyAccountId, driverId, sessionType, secondaryAuthToken }` | 发起音视频对讲 |
| `end_media` | `{ sessionHandle }` | 挂断 |
| `trigger_rescue` | `{ familyAccountId, driverId, secondaryAuthToken }` | 手动救援触发 |

**下行（云端 → APP）**：

| 消息 type | Payload |
|-----------|---------|
| `connection_established` | `{ connectionId, accountId }` |
| `ping` | `{ serverTime }` |
| `driver_status_snapshot` | `{ driverId, vehicleId, timestamp, activeAlertLevels, gpsLocation?, speed?, tripStatus, physiologicalSummary{heartRate,spo2,emotionIndex}?, windowStatus[]? }` ≥1Hz |
| `alert_triggered` | `{ alertId, alertType, riskLevel, occurredAt, resolvedAt?, tripId, gpsLocation? }` |
| `access_granted` | `{ driverId, sessionToken, sparkRTCRoomId, sparkRTCJoinToken, reason }` |
| `access_revoked` | `{ driverId, reason }` |
| `subscribe_status_ack` | `{ subscriptionId, initialSnapshot }` |
| `rescue_triggered` | `{ rescueRequestId, rescueReportId, status }` |
| `token_renewed` | `{ sparkRTCRoomId, sparkRTCJoinToken, expiresAt }` |
| `error` | `{ code, message }` |

**连接管理**：心跳 30s PING/10s PONG 超时；重连 1s→2s→4s→8s→16s（最多 5 次）；单家属最多 1 连接；每驾驶员最多 3 家属订阅。

#### 车队大屏（`wss://.../ws/fleet?token=<JWT>`）

| 消息 type | Payload |
|-----------|---------|
| `l3_alert` | `{ fleetId, driverId, vehicleId, alertType, occurredAt, gpsLocation }` |
| `performance_warning` | `{ driverId, driverName, score, scorePeriod, primaryPenaltyItems[], occurredAt }` |

---

### 2.6 SparkRTC 音视频流（设计，未实现）

| 属性 | 说明 |
|------|------|
| 协议 | WebRTC (SparkRTC SDK)，SRTP 端到端加密 |
| 信令 | 云端 S3 作为信令中继，经 WebSocket/MQTT 下发入房 Token |
| 房间 ID | `room-{driverId}-{timestamp}` |
| 最大会话 | 10 分钟（高危失能豁免，Token 到期前 1 分钟自动续签） |
| 视频编码 | H.264, CIF/VGA/720P 自适应, ≤500kbps |
| 音频编码 | Opus 48kHz 32kbps |
| 角色 | `subscriber`(家属端) / `publisher`(车机端) |
| 权限授予 | 常规 60s 授予 / 高危自动激活 / 遮挡恢复 |
| 权限撤销 | 风险下降 / 摄像头遮挡 / 驾驶员禁用 |
| 驾驶员物理遮挡权 | HMI 一键断开视频流（保留音频或完全挂断） |

---

### 2.7 车机 HMI → 边缘服务（进程内调用）

| 功能 | 方式 | 内容 |
|------|------|------|
| 查询干预指令 | Java 方法调用 | `S2.queryInterventionStatus(tripId)` → 干预指令集合 |
| 查询传感器状态 | 聚合根读取 | Vehicle.SensorStatus 集合 |
| 消费遮挡事件 | 进程内事件 | `CameraOcclusionDetectedEvent/RemovedEvent` |
| 消费家属接入通知 | 进程内事件 | `FamilyAccessGrantedEvent` |
| 上报覆盖信号 | Java 方法调用 | `S2.reportOverride(OverrideSignal{type:STEER/BRAKE/ACCELERATE})` |
| 驾驶员物理遮挡 | 触发事件 | `CameraOcclusionDetectedEvent` |

---

## 三、实现状态总览

| 通道 | 状态 | 代码位置 |
|------|:----:|----------|
| gRPC (Python→Java) | ✅ 已实现 | `perception/sidecar/server.py`, `server/.../infra/edge/DmsPerceptionAdapter.java` |
| REST API (前端→后端) | ⚠️ 部分实现 | Controller 已编写，后端部分 stub |
| WebSocket (前端→后端) | ⚠️ 前端已实现，后端未实现 | `frontend/api/BaseWebSocket.ts`，`server/interfaces/websocket/.gitkeep` |
| MQTT (边缘→IoTDA→云端) | ❌ 已设计，未实现 | `server/interfaces/mqtt/.gitkeep`，契约见 `docs/ood_interface.md` §2 |
| EdgeEventBus (同步进程内) | ✅ 已实现 | `server/.../infra/eventbus/EdgeEventBus.java` |
| CloudEventBus (Outbox异步) | ✅ 已实现 | `server/.../infra/eventbus/CloudEventBus.java` |
| SparkRTC 音视频流 | ❌ 已设计，未实现 | `frontend/entry/src/main/ets/rtc/.gitkeep` |
| 数据库 (JPA + Flyway) | ✅ 已实现 | Flyway V1–V4, 13 张表 |
| Port Adapters (SMN/OBS等) | ⚠️ 全部 stub | `server/.../infra/adapter/*.java`（仅打日志） |

