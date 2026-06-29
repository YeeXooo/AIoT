# 车载安全监测系统 API/接口层 OOD 设计方案（a_v2 / v2）

> 本文档为「智能物联——基于多传感器融合的车载安全监测系统」的**接口/API 层**架构级 OOD 设计方案，承接领域层 OOD（`docs/ood_domain.md`）与应用层 OOD（`docs/ood_application.md`）。本文档定义六个应用服务对外暴露的完整 API 契约（REST/MQTT/WebSocket）、MQTT 设备-云通信主题路由、家属 APP WebSocket/SparkRTC 信令集成、ArkTS 前端对接数据模型，以及全链路安全设计。
> 技术栈：前端 ArkTS（HarmonyOS），后端 Java Spring Boot，设备-云通信基于华为云 IoTDA（MQTT），实时音视频基于华为云 SparkRTC。

---

## 一、REST API 契约

所有 API 端点均遵循 RESTful 风格，按 OpenAPI 3.0 风格描述。认证头统一为 `Authorization: Bearer <JWT>`。基础路径：`/api/v1`。

### 1.1 S1 RiskMonitoringService — 风险监测服务

对应应用层 `IRiskMonitoringService` / `RiskMonitoringServiceImpl`。

> **注意**：S1 横跨边缘侧与云端两侧部署。`startMonitoringSession`、`processSensorReading`、`startLifeDetection` 仅在边缘侧内部调用（不经外部 API 网关暴露）。对外暴露的 REST API 仅含 `getDriverRiskStatus` 和 `queryAlertHistory`。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **查询驾驶员当前风险状态** | GET | `/api/v1/drivers/{driverId}/risk-status` | — | — | `GetDriverRiskStatusResponse` | 200 | JWT |
| **查询历史告警列表** | GET | `/api/v1/drivers/{driverId}/alerts` | — | `alertType`, `riskLevel`, `startTime`, `endTime`, `page`, `size` | `QueryAlertHistoryResponse` | 200 | JWT |

#### GetDriverRiskStatusResponse

```json
{
  "hasActiveTrip": true,
  "activeAlerts": [
    { "alertType": "FATIGUE", "riskLevel": "L2_WARNING" }
  ],
  "derivedStatusColor": "YELLOW"
}
```

**activeAlerts** 为当前活跃风险及其等级的映射列表。`derivedStatusColor` 取值：`GREEN` / `YELLOW` / `RED`（无风险/L1 → GREEN, L2 → YELLOW, L3 → RED）。

#### QueryAlertHistoryResponse

```json
{
  "alerts": [
    {
      "alertId": "alert-uuid-001",
      "alertType": "FATIGUE",
      "riskLevel": "L3_CRITICAL",
      "occurredAt": "2026-06-29T08:30:00Z",
      "resolvedAt": "2026-06-29T08:35:00Z",
      "tripId": "trip-uuid-042",
      "gpsLocation": { "latitude": 31.2304, "longitude": 121.4737 }
    }
  ],
  "totalCount": 42
}
```

`gpsLocation` 为可选字段（含 `latitude`、`longitude`），与 MQTT SafetyAlertEvent 的 `gps` 字段保持一致。无 GPS 数据时该字段为 `null`。

**查询参数说明**：
- `alertType` (optional): `FATIGUE` / `DISTRACTION` / `ROAD_RAGE` / `LIFE_DETECTION` / `COLLISION_DISABILITY` / `PERFORMANCE_WARNING`
- `riskLevel` (optional): `L1_HINT` / `L2_WARNING` / `L3_CRITICAL`
- `startTime`, `endTime` (optional): ISO 8601 时间戳
- `page` (default 1), `size` (default 20, max 100)

**错误响应**：
- `400` — 参数无效
- `404` — 驾驶员不存在
- `503` — 数据源不可用

---

### 1.2 S2 InterventionService — 干预执行服务

对应应用层 `IInterventionService` / `InterventionServiceImpl`。

`reportOverride` 仅在边缘侧内部调用（不经外部 API 网关暴露）。

| 端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|--------|:--:|:--:|
| **查询当前干预状态** | GET | `/api/v1/trips/{tripId}/interventions/active` | — | `QueryInterventionStatusResponse` | 200 | JWT |
| **查询干预历史** | GET | `/api/v1/trips/{tripId}/interventions/history` | — | `QueryInterventionHistoryResponse` | 200 | JWT |

#### QueryInterventionStatusResponse

```json
{
  "activeInterventions": [
    {
      "interventionId": "iv-001",
      "interventionType": "AMBIENT_LIGHT_COLOR",
      "targetDevice": "DASHBOARD_LED",
      "parameters": { "color": "ORANGE" },
      "priority": 2,
      "issuedAt": "2026-06-29T08:30:00.500Z"
    }
  ]
}
```

`interventionType` 取值：`AMBIENT_LIGHT_COLOR` / `VOICE_BROADCAST` / `SEAT_VIBRATION` / `HAZARD_LIGHTS` / `AIR_CONDITIONING` / `AUDIO_PLAYBACK` / `CAN_DECELERATION_REQUEST` / `NAVIGATE_DECELERATION` / `NAVIGATE_TO_SHOULDER` / `ALERT`。

#### QueryInterventionHistoryResponse

```json
{
  "interventions": [ { "...": "同 InterventionSummary" } ],
  "totalCount": 15
}
```

查询参数：`page`, `size`, `startTime`, `endTime`（均为 optional）。

---

### 1.3 S3 RemoteGuardianshipService — 远程监护服务

对应应用层 `IRemoteGuardianshipService` / `RemoteGuardianshipServiceImpl`。

状态订阅与推送通过 WebSocket 实现（见 §3），以下为 REST 端点。

| 端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|--------|:--:|:--:|
| **请求建立音视频对讲** | POST | `/api/v1/guardianship/media-session` | `RequestMediaSessionRequest` | `RequestMediaSessionResponse` | 201 | JWT |
| **终止音视频会话** | DELETE | `/api/v1/guardianship/media-session/{sessionHandle}` | — | — | 204 | JWT |
| **更新通知偏好** | PUT | `/api/v1/guardianship/notification-preference` | `UpdateNotificationPreferenceRequest` | — | 204 | JWT |
| **一键手动救援触发** | POST | `/api/v1/guardianship/manual-rescue` | `TriggerManualRescueRequest` | `TriggerManualRescueResponse` | 201 | JWT |
| **远程车窗控制** | POST | `/api/v1/guardianship/window-control` | `ControlVehicleWindowRequest` | — | 202 | JWT |
| **查询车窗状态** | GET | `/api/v1/vehicles/{vehicleId}/windows` | — | `QueryWindowStatusResponse` | 200 | JWT |
| **查询家属监护权限** | GET | `/api/v1/guardianship/{driverId}/permissions` | — | — | `QueryGuardianshipPermissionsResponse` | 200 | JWT |
| **签发 SparkRTC Token** | POST | `/api/v1/sparkrtc/token` | `IssueSparkRTCTokenRequest` | `IssueSparkRTCTokenResponse` | 200 | JWT |

> **202 Accepted 说明**：`controlVehicleWindow` 返回 202（指令已下发至 IoTDA，不等同于车窗操作物理完成）。前端应进一步轮询 `queryWindowStatus` 确认执行结果。

#### RequestMediaSessionRequest

```json
{
  "familyAccountId": "account-042",
  "driverId": "driver-007",
  "sessionType": "VIDEO",
  "secondaryAuthToken": "otp-token-xxx"
}
```

`sessionType`: `AUDIO` | `VIDEO`

#### RequestMediaSessionResponse

```json
{
  "sessionHandle": "media-session-uuid",
  "sessionToken": "eyJhbGciOi...",
  "sparkRTCRoomId": "room-20260629-001",
  "sparkRTCJoinToken": "join-token-for-frontend"
}
```

- `sessionToken`：家属端接入 SparkRTC 的临时鉴权 token
- `sparkRTCRoomId`：SparkRTC 房间 ID
- `sparkRTCJoinToken`：前端加入 SparkRTC 房间的 join token

#### UpdateNotificationPreferenceRequest

```json
{
  "familyAccountId": "account-042",
  "driverId": "driver-007",
  "preferredRiskLevels": ["L2_WARNING", "L3_CRITICAL"]
}
```

未设置（空数组）时默认接收全部等级。

#### TriggerManualRescueRequest

```json
{
  "familyAccountId": "account-042",
  "driverId": "driver-007",
  "secondaryAuthToken": "otp-token-xxx"
}
```

#### TriggerManualRescueResponse

```json
{
  "rescueRequestId": "rescue-req-uuid",
  "status": "PENDING"
}
```

`status`: `PENDING` | `CONFIRMED` | `REJECTED`

#### ControlVehicleWindowRequest

```json
{
  "familyAccountId": "account-042",
  "driverId": "driver-007",
  "windowOperation": "PARTIAL_OPEN",
  "windowPosition": "REAR_LEFT",
  "secondaryAuthToken": "otp-token-xxx"
}
```

`windowOperation`: `OPEN` | `CLOSE` | `PARTIAL_OPEN`
`windowPosition`: `FRONT_LEFT` | `FRONT_RIGHT` | `REAR_LEFT` | `REAR_RIGHT`

#### QueryWindowStatusResponse

```json
{
  "windowStatuses": [
    {
      "windowPosition": "REAR_LEFT",
      "state": "OPEN",
      "lastOperation": "OPEN",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:05Z"
    }
  ]
}
```

`state`: `OPEN` | `CLOSED` | `PARTIAL` | `UNKNOWN` | `MOVING`
`lastOperationResult`: `SUCCESS` | `TIMEOUT` | `FAILED` | `PENDING`

#### QueryGuardianshipPermissionsResponse

```json
{
  "familyAccountId": "account-042",
  "driverId": "driver-007",
  "permissions": [
    {
      "permissionType": "MEDIA_CALL",
      "granted": true,
      "grantedAt": "2026-06-29T08:00:00Z",
      "expiresAt": "2026-06-29T08:10:00Z"
    },
    {
      "permissionType": "WINDOW_CONTROL",
      "granted": true,
      "grantedAt": "2026-06-29T08:00:00Z",
      "expiresAt": null
    },
    {
      "permissionType": "MANUAL_RESCUE",
      "granted": true,
      "grantedAt": "2026-06-29T08:00:00Z",
      "expiresAt": null
    }
  ],
  "careRelationship": {
    "status": "ACTIVE",
    "establishedAt": "2026-01-15T00:00:00Z"
  }
}
```

`permissionType`: `MEDIA_CALL` | `WINDOW_CONTROL` | `MANUAL_RESCUE` | `STATUS_MONITORING`
`careRelationship.status`: `ACTIVE` | `SUSPENDED` | `REVOKED`

> **隐私保护**：此端点仅返回与请求方 `accountId` 关联的监护权限；拒绝查询非本人持有的权限关系（见 §5.6 隐私校验规则）。

#### IssueSparkRTCTokenRequest

```json
{
  "roomId": "room-driver007-20260629083000",
  "userId": "family-account-042",
  "role": "subscriber"
}
```

`role`: `subscriber`（家属端） / `publisher`（车机端）

#### IssueSparkRTCTokenResponse

```json
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-06-29T08:40:00Z"
}
```

Token 有效期 10 分钟，与房间最大会话时长一致。由 S3 RemoteGuardianshipService 签发，后端集成华为云 SparkRTC SDK Token 生成 API。

**错误响应**：
- `401` — 二次身份验证未通过（`SecondaryAuthRequired`）
- `403` — 权限不足（`PermissionDenied`，含具体原因：`NotRelated` / `NoAuthorization` / `AuthorizationExpired` / `AuthorizationRevoked`）
- `409` — 订阅数超限（`SubscriptionLimitExceeded`，每驾驶员最多 3 个家属订阅）
- `503` — 信令通道不可达（`SessionEstablishFailed`）
- `504` — 车窗控制超时（`WindowControlTimeout`，30s 内未收到 IoTDA Ack）

---

### 1.4 S4 FleetManagementService — 车队管理服务

对应应用层 `IFleetManagementService` / `FleetManagementServiceImpl`。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **车队疲劳分布看板** | GET | `/api/v1/fleet/{fleetId}/fatigue-distribution` | — | `startTime`, `endTime` (optional) | `GetFatigueDistributionResponse` | 200 | JWT |
| **脱线车辆列表** | GET | `/api/v1/fleet/{fleetId}/offline-vehicles` | — | — | `GetOfflineVehiclesResponse` | 200 | JWT |
| **车辆轨迹查询** | GET | `/api/v1/fleet/{fleetId}/trajectory` | — | `vehicleId`, `driverId`, `startTime`, `endTime`, `page`, `size` | `QueryTrajectoryResponse` | 200 | JWT |
| **高风险司机钻取** | GET | `/api/v1/fleet/{fleetId}/high-risk-drivers` | — | `riskLevel` (required), `page`, `size` | `DrillDownHighRiskResponse` | 200 | JWT |
| **驾驶行为报告生成** | POST | `/api/v1/fleet/reports` | `GenerateReportRequest` | — | `GenerateReportResponse` | 201 | JWT |
| **报告文件下载** | GET | `/api/v1/fleet/reports/{reportId}/download` | — | `format` (`pdf`/`xlsx`) | 二进制文件流 | 200 | JWT |
| **绩效预警订阅** | POST | `/api/v1/fleet/performance-warning-subscription` | `SubscribePerformanceWarningRequest` | — | `SubscribePerformanceWarningResponse` | 201 | JWT |

#### GetFatigueDistributionResponse

```json
{
  "distribution": { "L1_HINT": 0.45, "L2_WARNING": 0.30, "L3_CRITICAL": 0.25 },
  "heatmapData": [
    { "latitude": 31.2304, "longitude": 121.4737, "riskIntensity": 0.85 }
  ],
  "dataFreshness": "FRESH",
  "generatedAt": "2026-06-29T08:30:00Z"
}
```

`dataFreshness`: `FRESH` | `STALE`（超时时返回缓存结果并标注）

#### GetOfflineVehiclesResponse

```json
{
  "offlineVehicles": [
    {
      "vehicleId": "vehicle-013",
      "licensePlate": "沪A·12345",
      "driverId": "driver-007",
      "driverName": "张三",
      "offlineReason": "SENSOR_FAULT",
      "offlineSince": "2026-06-29T08:25:00Z",
      "lastHeartbeat": "2026-06-29T08:24:55Z"
    }
  ]
}
```

`offlineReason`: `SENSOR_FAULT` | `COMMUNICATION_LOST`

#### QueryTrajectoryResponse

```json
{
  "trajectoryPoints": [
    { "timestamp": "2026-06-29T08:01:00Z", "latitude": 31.2304, "longitude": 121.4737, "speed": 65.0 }
  ],
  "totalCount": 3600
}
```

查询参数：`vehicleId` 和 `driverId` 至少提供一个。若两者同时提供但不匹配，返回空序列（`dataConsistency = INCONSISTENT`）。

#### DrillDownHighRiskResponse

```json
{
  "drivers": [
    {
      "driverId": "driver-007",
      "driverName": "张三",
      "compositeRiskScore": 55.0,
      "latestTripSummary": { "tripId": "trip-042", "startTime": "...", "endTime": "...", "score": 48.0 },
      "primaryPenaltyItems": ["重度疲劳 ×3", "路怒 ×1", "急刹 ×5"]
    }
  ],
  "totalCount": 8
}
```

#### GenerateReportRequest

```json
{
  "driverId": "driver-007",
  "timeRange": { "start": "2026-06-01T00:00:00Z", "end": "2026-06-30T23:59:59Z" },
  "reportType": "MONTHLY"
}
```

`reportType`: `WEEKLY` | `MONTHLY` | `QUARTERLY`

#### GenerateReportResponse

```json
{
  "reportId": "report-uuid",
  "reportData": {
    "reportId": "report-uuid",
    "driverId": "driver-007",
    "timeRange": { "start": "2026-06-01T00:00:00Z", "end": "2026-06-30T23:59:59Z" },
    "reportType": "MONTHLY",
    "drivingBehaviorSummary": {
      "overallScore": 72.5,
      "subScores": { "fatigueScore": 70.0, "distractionScore": 85.0, "abnormalDrivingScore": 65.0 },
      "trendVsLastPeriod": -3.5
    },
    "riskDistribution": { "FATIGUE": 12, "DISTRACTION": 5, "ROAD_RAGE": 2 },
    "penaltyBreakdown": [
      { "category": "疲劳", "penaltyScore": 15.0, "topViolations": ["重度疲劳 ×3"] }
    ],
    "totalMileage": 2850.5,
    "totalDrivingTime": "PT45H30M",
    "generatedAt": "2026-06-29T08:30:00Z"
  },
  "downloadUrl": "https://api.example.com/api/v1/fleet/reports/report-uuid/download?format=pdf",
  "isEmpty": false
}
```

报告生成 SLA：≤15 秒。超时返回 `504` + `AppError.ReportGenerationTimeout`；所选时间范围内无数据时 `isEmpty = true`（非错误）。

#### SubscribePerformanceWarningResponse

```json
{
  "subscriptionId": "sub-uuid"
}
```

绩效预警通过 WebSocket 主动推送至管理端（见 §3）。

---

### 1.5 S5 EmergencyRescueService — 应急救援服务

对应应用层 `IEmergencyRescueService` / `EmergencyRescueServiceImpl`。

| 端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|--------|:--:|:--:|
| **SOS 报告确认** | POST | `/api/v1/emergency/sos-confirm` | `ConfirmSOSReportRequest` | — | 204 | JWT (RESCUE) |
| **签发救援授权凭证** | POST | `/api/v1/emergency/rescue-tokens` | `IssueRescueTokenRequest` | `IssueRescueTokenResponse` | 201 | JWT (RESCUE) |
| **校验并消费救援凭证** | POST | `/api/v1/emergency/rescue-tokens/verify` | `VerifyRescueTokenRequest` | `VerifyRescueTokenResponse` | 200 | JWT (RESCUE) |
| **查询救援历史** | GET | `/api/v1/emergency/rescue-history` | — | `QueryRescueHistoryResponse` | 200 | JWT |

#### ConfirmSOSReportRequest

```json
{
  "rescueReportId": "report-uuid",
  "ackToken": "ack-token-from-rescue-center"
}
```

#### IssueRescueTokenRequest

```json
{
  "rescueReportId": "report-uuid",
  "authorizedOperations": ["RemoteUnlock", "HealthProfileAccess"],
  "validityDurationSeconds": 1800
}
```

`authorizedOperations`: `RemoteUnlock` / `HealthProfileAccess` / `RemoteWindowControl`

#### IssueRescueTokenResponse

```json
{
  "rescueToken": {
    "tokenId": "token-uuid",
    "targetVehicleId": "vehicle-013",
    "targetDriverId": "driver-007",
    "authorizedOperations": ["RemoteUnlock"],
    "issuedAt": "2026-06-29T08:30:00Z",
    "expiresAt": "2026-06-29T09:00:00Z",
    "signature": "..."
  }
}
```

#### VerifyRescueTokenRequest

```json
{
  "rescueToken": { "...": "完整凭证对象" },
  "requestedOperation": "RemoteUnlock",
  "targetVehicleId": "vehicle-013"
}
```

#### VerifyRescueTokenResponse

```json
{
  "result": "VALID"
}
```

#### QueryRescueHistoryResponse

```json
{
  "rescueRecords": [
    {
      "rescueReportId": "report-uuid",
      "driverId": "driver-007",
      "driverName": "张三",
      "licensePlate": "沪A·12345",
      "triggerType": "COLLISION_DISABILITY",
      "status": "CONFIRMED",
      "occurredAt": "2026-06-29T08:30:00Z"
    }
  ],
  "totalCount": 5
}
```

`triggerType`: `COLLISION_DISABILITY` / `MANUAL` / `LIFE_DETECTION`
`status`: `SENT` / `CONFIRMED` / `PENDING_RETRY` / `MANUAL_ESCALATION`

查询参数：`driverId` (optional), `vehicleId` (optional), `startTime` (optional), `endTime` (optional), `page`, `size`

**错误响应**：
- `403` — 授权凭证过期 / 已消费 / 角色不匹配 / 操作未授权（`AccessDenied`）
- `409` — 并发消费冲突（`ConcurrentConsumption`）

---

### 1.6 S6 OTAManagementService — OTA 升级管理服务

对应应用层 `IOTAManagementService` / `OTAManagementServiceImpl`。

| 端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|--------|:--:|:--:|
| **创建升级任务** | POST | `/api/v1/ota/upgrade-tasks` | `CreateUpgradeTaskRequest` | `CreateUpgradeTaskResponse` | 201 | JWT |
| **批量查询升级进度** | GET | `/api/v1/ota/upgrade-progress` | — | `QueryUpgradeProgressResponse` | 200 | JWT |
| **触发回滚** | POST | `/api/v1/ota/rollback` | `TriggerRollbackRequest` | `TriggerRollbackResponse` | 200 | JWT |
| **查询升级历史** | GET | `/api/v1/ota/upgrade-history/{vehicleId}` | — | `QueryUpgradeHistoryResponse` | 200 | JWT |
| **取消升级任务** | DELETE | `/api/v1/ota/upgrade-tasks/{taskId}` | — | — | `CancelUpgradeTaskResponse` | 200 | JWT |

#### CreateUpgradeTaskRequest

```json
{
  "targetVehicleIds": ["vehicle-001", "vehicle-002"],
  "targetVersion": { "major": 2, "minor": 1, "patch": 0, "buildNumber": "build-20260615" },
  "upgradeOptions": {
    "batchStrategy": "BY_REGION",
    "scheduledWindow": { "start": "2026-06-30T02:00:00Z", "end": "2026-06-30T05:00:00Z" },
    "forceUpgrade": false
  },
  "idempotencyKey": "idem-key-20260629-001"
}
```

批量上限：100 辆车。`idempotencyKey` 用于幂等保证——同一 key 的重复请求返回已创建任务列表。

#### CreateUpgradeTaskResponse

```json
{
  "createdTaskIds": ["task-uuid-001"],
  "skippedVehicles": [
    { "vehicleId": "vehicle-002", "reason": "UPGRADE_IN_PROGRESS" }
  ]
}
```

#### QueryUpgradeProgressResponse

```json
{
  "progressEntries": [
    {
      "vehicleId": "vehicle-001",
      "currentStage": "TRANSMITTING",
      "progressPercent": 67.5,
      "estimatedRemainingSeconds": 45
    }
  ]
}
```

查询参数：`vehicleIds` (comma-separated, required)

`currentStage`: `PENDING` / `TRANSMITTING` / `VERIFYING` / `READY` / `UPGRADING` / `COMPLETED` / `ROLLED_BACK`

#### TriggerRollbackRequest

```json
{
  "vehicleId": "vehicle-001",
  "reason": "升级后HMI渲染异常"
}
```

#### TriggerRollbackResponse

```json
{
  "vehicleId": "vehicle-001",
  "newStatus": "ROLLED_BACK"
}
```

#### QueryUpgradeHistoryResponse

```json
{
  "entries": [
    {
      "taskId": "task-uuid-001",
      "oldVersion": { "major": 2, "minor": 0, "patch": 0, "buildNumber": "build-20260501" },
      "newVersion": { "major": 2, "minor": 1, "patch": 0, "buildNumber": "build-20260615" },
      "duration": "PT3M45S",
      "finalStatus": "SUCCEEDED"
    }
  ],
  "totalCount": 12
}
```

查询参数：`page`, `size`

#### CancelUpgradeTaskResponse

```json
{
  "taskId": "task-uuid-001",
  "previousStatus": "PENDING",
  "cancelledAt": "2026-06-29T08:35:00Z"
}
```

`previousStatus`: 取消前的任务状态。仅 `PENDING` 或 `TRANSMITTING` 阶段的任务允许取消，已进入 `VERIFYING`、`UPGRADING` 及终态（`COMPLETED` / `ROLLED_BACK`）的任务拒绝取消。

**错误响应**：
- `400` — 批量超限（>100）：`BatchSizeExceeded`
- `409` — 目标车辆已有进行中升级 / 已处于终态（`COMPLETED` / `ROLLED_BACK`）；任务不可取消（非 PENDING/TRANSMITTING 阶段）：`UpgradeTaskNotCancellable`

---

## 二、MQTT 主题设计

边缘侧终端通过华为云 IoTDA（MQTT）与云端通信。以下主题路由表覆盖全部设备-云通信场景，按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）。

### 2.1 主题路由总表

| 方向 | Topic 模板 | QoS | 数据分类 | Payload 说明 |
|:--:|------|:--:|------|------|
| 上报 | `{deviceId}/sensor/${sensorType}/up` | 1 | 流式感知数据 | 单帧 SensorReading（DMS/生理/语音/雷达），频率按通道（DMS ≥10Hz，生理 ≥1Hz，语音 ≥1Hz，雷达按需），topic 按 sensorType 分流 |
| 上报 | `{deviceId}/trip/status/up` | 1 | 行程状态 | Trip 状态变更事件（点火/熄火/行程 ID、时间戳），精度为行程级 |
| 上报 | `{deviceId}/alert/up` | 1 | 告警事件 | SafetyAlertEvent 摘要（告警 ID、类型、等级、时间戳、GPS），边缘判定后实时上报 |
| 上报 | `{deviceId}/physiological/snapshot/up` | 1 | 生理体征快照 | PhysiologicalSnapshot（心率、血氧、情绪指数、时间戳），≥1Hz |
| 上报 | `{deviceId}/vehicle/state/up` | 1 | 车辆状态遥测 | VehicleStateSnapshot（车速、车门锁、加速度），≥1Hz |
| 上报 | `{deviceId}/status/heartbeat/up` | 0 | 心跳 | 设备心跳（终端序列号、时间戳），默认每 30s 上报一次，包含传感器自检摘要 |
| 下指令 | `{deviceId}/cmd/intervention/down` | 1 | 干预指令 | InterventionInstruction 集合（云端关联下发干预指令，如车队管理员远程鸣笛等） |
| 下指令 | `{deviceId}/cmd/window/down` | 1 | 车窗控制 | 车窗控制指令（车窗位置 + OPEN/CLOSE/PARTIAL_OPEN），家属 APP 发起，云端经 IoTDA 下发 |
| 下指令 | `{deviceId}/cmd/door/unlock/down` | 1 | 车门解锁 | 远程车门解锁指令（救援授权场景），经 RescueAuthorizationToken 校验后下发 |
| 下指令 | `{deviceId}/cmd/ota/down` | 1 | OTA 升级包 | 固件升级包分片下发（二进制载荷），支持断点续传偏移量 |
| 下指令 | `{deviceId}/cmd/ota/rollback/down` | 1 | OTA 回滚指令 | 固件回滚指令（目标版本/回滚原因） |
| 响应 | `{deviceId}/cmd/${commandId}/ack` | 1 | 指令执行确认 | 对下指令的 Ack（指令 ID、执行结果、失败原因） |
| 上报 | `{deviceId}/sensor/fault/up` | 1 | 传感器故障 | SensorFailureEvent（故障传感器列表、时间戳），3s SLA |
| 上报 | `{deviceId}/sensor/occlusion/up` | 1 | 摄像头遮挡 | CameraOcclusionDetectedEvent / CameraOcclusionRemovedEvent |
| 上报 | `{deviceId}/driver/override/up` | 1 | 驾驶员覆盖信号 | OverrideSignal（STEER/BRAKE/ACCELERATE + 时间戳） |
| 上报 | `{deviceId}/trip/score/up` | 1 | 行程评分 | TripScore（评分值、行程 ID、扣分项明细），行程结束时上报 |
| 上报 | `{deviceId}/voice/evidence/up` | 1 | 路怒语音存证 | 加密音频片段（AES-256-GCM），行程结束时或存储压力时批量上传 |
| 推送→APP | `family/{accountId}/alert/push` | 1 | 家属告警推送 | AlertTriggeredEvent 推送（经 NotificationPreference 过滤），≤10s（活体遗留）或秒级（常规告警） |
| 推送→APP | `family/{accountId}/status/push` | 1 | 家属状态快照 | DriverStatusSnapshot（状态色 + GPS + 车速 + 生理摘要），≥1Hz |
| 推送→APP | `family/{accountId}/access/granted` | 1 | 家属权限授予 | FamilyAccessGrantedEvent（授权后自动推送，含 session token） |
| 推送→APP | `family/{accountId}/access/revoked` | 1 | 家属权限撤销 | FamilyAccessRevokedEvent（撤销后断开对讲/视频） |
| 推送→大屏 | `fleet/{fleetId}/alert/push` | 1 | 车队告警推送 | L3 高危告警实时推送（驱动看板缓存失效），秒级 |
| 推送→大屏 | `fleet/{fleetId}/performance-warning/push` | 1 | 绩效预警推送 | PerformanceWarningEvent（司机 ID、评分值、扣分摘要） |
| 推送→APP | `app/{accountId}/rescue/confirm` | 1 | SOS 确认通知 | SOS 上报结果通知（CONFIRMED / PENDING_RETRY） |

### 2.2 Payload JSON Schema（核心）

#### SensorReading（流式感知数据上报）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["timestamp", "sensorId", "sensorType", "values"],
  "properties": {
    "timestamp": { "type": "string", "format": "date-time" },
    "sensorId": { "type": "string" },
    "sensorType": {
      "type": "string",
      "enum": ["DMS_CAMERA", "MILLIMETER_WAVE_RADAR", "ACCELEROMETER", "PHYSIOLOGICAL_MONITOR", "MICROPHONE", "ACOUSTIC", "REAR_IR_CAMERA"]
    },
    "values": {
      "type": "object",
      "description": "传感器读数键值对，键名按传感器类型约定",
      "additionalProperties": { "type": "number" }
    }
  }
}
```

各 `sensorType` 的 `values` 最小字段约定：

| sensorType | values 最小字段 |
|------|------|
| `DMS_CAMERA` | `PERCLOS`, `yawnFreq`, `headNodFreq`, `gazeDeviationCumulative`, `handsOffWheel` |
| `MILLIMETER_WAVE_RADAR` | `microMotionDetected` (0/1), `breathingRate`, `motionConfidence` |
| `ACCELEROMETER` | `accelX`, `accelY`, `accelZ`, `collisionImpact`, `hardBraking`, `hardAcceleration` |
| `PHYSIOLOGICAL_MONITOR` | `heartRate`, `spo2`, `hrResting`, `rrInterval` (逐拍R-R间期) |
| `MICROPHONE` / `ACOUSTIC` | `spl_dB`, `keywordsDetected`, `speechRate` |
| `REAR_IR_CAMERA` | `frameRef` (视频帧引用，不直接传输原始像素) |

#### SafetyAlertEvent（告警事件上报）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["alertId", "alertType", "riskLevel", "occurredAt", "tripId"],
  "properties": {
    "alertId": { "type": "string" },
    "alertType": {
      "type": "string",
      "enum": ["FATIGUE", "DISTRACTION", "ROAD_RAGE", "LIFE_DETECTION", "COLLISION_DISABILITY", "PERFORMANCE_WARNING"]
    },
    "riskLevel": {
      "type": "string",
      "enum": ["L1_HINT", "L2_WARNING", "L3_CRITICAL"]
    },
    "occurredAt": { "type": "string", "format": "date-time" },
    "resolvedAt": { "type": ["string", "null"], "format": "date-time" },
    "tripId": { "type": "string" },
    "gps": {
      "type": "object",
      "required": ["latitude", "longitude"],
      "properties": {
        "latitude": { "type": "number" },
        "longitude": { "type": "number" }
      }
    },
    "featureSnapshot": {
      "type": "object",
      "description": "告警触发时的异常特征快照，字段随 AlertType 不同",
      "additionalProperties": true
    }
  }
}
```

#### DriverStatusSnapshot（家属状态快照推送）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["driverId", "vehicleId", "timestamp", "activeAlertLevels", "tripStatus"],
  "properties": {
    "driverId": { "type": "string" },
    "vehicleId": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "activeAlertLevels": {
      "type": "object",
      "additionalProperties": { "type": "string", "enum": ["L1_HINT", "L2_WARNING", "L3_CRITICAL"] }
    },
    "gpsLocation": {
      "type": ["object", "null"],
      "properties": {
        "latitude": { "type": "number" },
        "longitude": { "type": "number" }
      }
    },
    "speed": { "type": ["number", "null"] },
    "tripStatus": { "type": "string", "enum": ["NOT_STARTED", "ACTIVE", "COMPLETED"] },
    "physiologicalSummary": {
      "type": ["object", "null"],
      "properties": {
        "heartRate": { "type": "number" },
        "spo2": { "type": "number" },
        "emotionIndex": { "type": "number" }
      }
    },
    "windowStatus": {
      "type": ["array", "null"],
      "items": {
        "type": "object",
        "properties": {
          "windowPosition": { "type": "string", "enum": ["FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"] },
          "state": { "type": "string", "enum": ["OPEN", "CLOSED", "PARTIAL", "UNKNOWN", "MOVING"] },
          "lastOperation": { "type": ["string", "null"] },
          "lastOperationResult": { "type": ["string", "null"], "enum": ["SUCCESS", "TIMEOUT", "FAILED", "PENDING"] },
          "updatedAt": { "type": "string", "format": "date-time" }
        }
      }
    }
  }
}
```

#### 干预指令下发（cmd/intervention/down）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "interventions", "issuedAt"],
  "properties": {
    "commandId": { "type": "string" },
    "issuedAt": { "type": "string", "format": "date-time" },
    "interventions": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["interventionId", "interventionType", "targetDevice", "priority"],
        "properties": {
          "interventionId": { "type": "string" },
          "interventionType": {
            "type": "string",
            "enum": ["AMBIENT_LIGHT_COLOR", "VOICE_BROADCAST", "SEAT_VIBRATION", "HAZARD_LIGHTS", "AIR_CONDITIONING", "AUDIO_PLAYBACK", "CAN_DECELERATION_REQUEST", "NAVIGATE_DECELERATION", "NAVIGATE_TO_SHOULDER", "ALERT"]
          },
          "targetDevice": { "type": "string" },
          "parameters": { "type": "object", "additionalProperties": true },
          "priority": { "type": "integer", "minimum": 1, "maximum": 3 }
        }
      }
    }
  }
}
```

#### 车窗控制指令（cmd/window/down）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "driverId", "windowOperation", "windowPosition", "issuedAt"],
  "properties": {
    "commandId": { "type": "string" },
    "driverId": { "type": "string" },
    "windowOperation": { "type": "string", "enum": ["OPEN", "CLOSE", "PARTIAL_OPEN"] },
    "windowPosition": { "type": "string", "enum": ["FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"] },
    "issuedAt": { "type": "string", "format": "date-time" },
    "secondaryAuthToken": { "type": "string" }
  }
}
```

#### 车门解锁指令（cmd/door/unlock/down）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "rescueTokenId", "targetVehicleId", "issuedAt"],
  "properties": {
    "commandId": { "type": "string" },
    "rescueTokenId": { "type": "string" },
    "targetVehicleId": { "type": "string" },
    "issuedAt": { "type": "string", "format": "date-time" },
    "rescueTokenSignature": { "type": "string" }
  }
}
```

#### OTA 升级指令（cmd/ota/down）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "taskId", "newVersion", "chunkIndex", "totalChunks", "payload"],
  "properties": {
    "commandId": { "type": "string" },
    "taskId": { "type": "string" },
    "newVersion": {
      "type": "object",
      "required": ["major", "minor", "patch", "buildNumber"],
      "properties": {
        "major": { "type": "integer" },
        "minor": { "type": "integer" },
        "patch": { "type": "integer" },
        "buildNumber": { "type": "string" }
      }
    },
    "chunkIndex": { "type": "integer", "minimum": 0 },
    "totalChunks": { "type": "integer", "minimum": 1 },
    "chunkOffset": { "type": "integer", "description": "断点续传偏移量（bytes）" },
    "chunkSize": { "type": "integer" },
    "payload": { "type": "string", "description": "Base64 编码的固件分片数据" },
    "checksum": { "type": "string", "description": "SHA-256 校验和" },
    "issuedAt": { "type": "string", "format": "date-time" }
  }
}
```

#### OTA 回滚指令（cmd/ota/rollback/down）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "vehicleId", "targetVersion", "reason", "issuedAt"],
  "properties": {
    "commandId": { "type": "string" },
    "vehicleId": { "type": "string" },
    "targetVersion": {
      "type": "object",
      "required": ["major", "minor", "patch", "buildNumber"],
      "properties": {
        "major": { "type": "integer" },
        "minor": { "type": "integer" },
        "patch": { "type": "integer" },
        "buildNumber": { "type": "string" }
      }
    },
    "reason": { "type": "string" },
    "issuedAt": { "type": "string", "format": "date-time" }
  }
}
```

#### 指令执行确认 Ack（cmd/{commandId}/ack）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["commandId", "result", "completedAt"],
  "properties": {
    "commandId": { "type": "string" },
    "result": { "type": "string", "enum": ["SUCCESS", "TIMEOUT", "FAILED", "PARTIAL"] },
    "failureReason": { "type": "string" },
    "completedAt": { "type": "string", "format": "date-time" },
    "detail": { "type": "object", "additionalProperties": true, "description": "操作结果详情（如车窗实际状态、OTA 校验结果等）" }
  }
}
```

#### 传感器故障（sensor/fault/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["deviceId", "failedSensors", "occurredAt"],
  "properties": {
    "deviceId": { "type": "string" },
    "failedSensors": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["sensorId", "sensorType", "failureType"],
        "properties": {
          "sensorId": { "type": "string" },
          "sensorType": { "type": "string", "enum": ["DMS_CAMERA", "MILLIMETER_WAVE_RADAR", "ACCELEROMETER", "PHYSIOLOGICAL_MONITOR", "MICROPHONE", "ACOUSTIC", "REAR_IR_CAMERA"] },
          "failureType": { "type": "string", "enum": ["HARDWARE_FAILURE", "COMMUNICATION_LOST", "CALIBRATION_ERROR", "SELF_TEST_FAILED"] },
          "errorCode": { "type": "string" }
        }
      }
    },
    "occurredAt": { "type": "string", "format": "date-time" }
  }
}
```

#### 摄像头遮挡事件（sensor/occlusion/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["deviceId", "sensorId", "eventType", "occurredAt"],
  "properties": {
    "deviceId": { "type": "string" },
    "sensorId": { "type": "string" },
    "eventType": { "type": "string", "enum": ["CAMERA_OCCLUSION_DETECTED", "CAMERA_OCCLUSION_REMOVED"] },
    "occlusionType": { "type": "string", "enum": ["PHYSICAL_COVER", "SOFTWARE_DISABLE", "DRIVER_DEACTIVATED"], "description": "仅在 eventType=CAMERA_OCCLUSION_DETECTED 时有值" },
    "occurredAt": { "type": "string", "format": "date-time" }
  }
}
```

#### 心跳（status/heartbeat/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["deviceId", "timestamp", "sequenceNumber"],
  "properties": {
    "deviceId": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "sequenceNumber": { "type": "integer", "description": "单调递增序号，用于检测心跳丢失" },
    "sensorSelfCheck": {
      "type": "object",
      "description": "传感器自检摘要",
      "properties": {
        "overallStatus": { "type": "string", "enum": ["OK", "DEGRADED", "FAULT"] },
        "failedSensorCount": { "type": "integer" },
        "lastSelfCheckAt": { "type": "string", "format": "date-time" }
      }
    },
    "systemMetrics": {
      "type": "object",
      "properties": {
        "cpuUsage": { "type": "number" },
        "memoryUsage": { "type": "number" },
        "storageAvailable": { "type": "number" },
        "uptimeSeconds": { "type": "integer" }
      }
    }
  }
}
```

#### 行程状态变更（trip/status/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["tripId", "driverId", "vehicleId", "eventType", "occurredAt"],
  "properties": {
    "tripId": { "type": "string" },
    "driverId": { "type": "string" },
    "vehicleId": { "type": "string" },
    "eventType": { "type": "string", "enum": ["TRIP_STARTED", "TRIP_COMPLETED"] },
    "occurredAt": { "type": "string", "format": "date-time" },
    "gps": {
      "type": "object",
      "properties": {
        "latitude": { "type": "number" },
        "longitude": { "type": "number" }
      }
    }
  }
}
```

#### 生理体征快照（physiological/snapshot/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["driverId", "timestamp"],
  "properties": {
    "driverId": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "heartRate": { "type": "number", "description": "bpm" },
    "spo2": { "type": "number", "description": "%" },
    "emotionIndex": { "type": "number", "description": "0.0–1.0" },
    "hrResting": { "type": "number" },
    "rrInterval": { "type": "number" }
  }
}
```

#### 车辆状态遥测（vehicle/state/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["vehicleId", "timestamp"],
  "properties": {
    "vehicleId": { "type": "string" },
    "timestamp": { "type": "string", "format": "date-time" },
    "speed": { "type": "number", "description": "km/h" },
    "doorLockState": { "type": "string", "enum": ["LOCKED", "UNLOCKED", "UNKNOWN"] },
    "acceleration": {
      "type": "object",
      "properties": {
        "x": { "type": "number" },
        "y": { "type": "number" },
        "z": { "type": "number" }
      }
    },
    "fuelLevel": { "type": "number" },
    "odometer": { "type": "number" }
  }
}
```

#### 驾驶员覆盖信号（driver/override/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["driverId", "tripId", "overrideType", "occurredAt"],
  "properties": {
    "driverId": { "type": "string" },
    "tripId": { "type": "string" },
    "overrideType": { "type": "string", "enum": ["STEER", "BRAKE", "ACCELERATE"] },
    "occurredAt": { "type": "string", "format": "date-time" },
    "gps": {
      "type": "object",
      "properties": {
        "latitude": { "type": "number" },
        "longitude": { "type": "number" }
      }
    }
  }
}
```

#### 行程评分（trip/score/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["tripId", "driverId", "score", "occurredAt"],
  "properties": {
    "tripId": { "type": "string" },
    "driverId": { "type": "string" },
    "score": { "type": "number", "minimum": 0, "maximum": 100 },
    "penaltyItems": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "category": { "type": "string", "enum": ["FATIGUE", "DISTRACTION", "ROAD_RAGE", "ABNORMAL_DRIVING"] },
          "penaltyScore": { "type": "number" },
          "violationCount": { "type": "integer" },
          "description": { "type": "string" }
        }
      }
    },
    "occurredAt": { "type": "string", "format": "date-time" }
  }
}
```

#### 路怒语音存证（voice/evidence/up）

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["recordId", "tripId", "driverId", "encryptedPayload", "encryptionMetadata", "occurredAt"],
  "properties": {
    "recordId": { "type": "string" },
    "tripId": { "type": "string" },
    "driverId": { "type": "string" },
    "encryptedPayload": { "type": "string", "description": "AES-256-GCM 加密的音频数据（Base64）" },
    "encryptionMetadata": {
      "type": "object",
      "properties": {
        "algorithm": { "type": "string", "const": "AES-256-GCM" },
        "keyId": { "type": "string", "description": "KMS 密钥 ID" },
        "iv": { "type": "string", "description": "初始化向量（Base64）" },
        "authTag": { "type": "string", "description": "GCM 认证标签" }
      }
    },
    "durationSeconds": { "type": "number" },
    "occurredAt": { "type": "string", "format": "date-time" }
  }
}
```

#### 次要 Payload 字段定义（表格式）

以下 Topic 的 Payload 以表格形式给出字段级定义：

**家属告警推送（family/{accountId}/alert/push）— AlertTriggeredEvent**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `alertId` | string | ✓ | 告警 ID |
| `alertType` | string (enum) | ✓ | `FATIGUE` / `DISTRACTION` / `ROAD_RAGE` / `LIFE_DETECTION` / `COLLISION_DISABILITY` / `PERFORMANCE_WARNING` |
| `riskLevel` | string (enum) | ✓ | `L1_HINT` / `L2_WARNING` / `L3_CRITICAL` |
| `driverId` | string | ✓ | 驾驶员 ID |
| `vehicleId` | string | ✓ | 车辆 ID |
| `occurredAt` | string (date-time) | ✓ | 告警触发时间 |
| `tripId` | string | ✓ | 关联行程 ID |
| `gps` | object | | `{ latitude, longitude }` |

**家属权限授予（family/{accountId}/access/granted）— FamilyAccessGrantedEvent**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `driverId` | string | ✓ | 驾驶员 ID |
| `sessionToken` | string | ✓ | 家属会话 Token |
| `sparkRTCRoomId` | string | ✓ | SparkRTC 房间 ID |
| `sparkRTCJoinToken` | string | ✓ | SparkRTC 入房 Token |
| `reason` | string (enum) | ✓ | `REGULAR_60S` / `EMERGENCY_ACTIVATION` / `OCCLUSION_RECOVERY` |

**家属权限撤销（family/{accountId}/access/revoked）— FamilyAccessRevokedEvent**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `driverId` | string | ✓ | 驾驶员 ID |
| `reason` | string (enum) | ✓ | `RISK_DECLINED` / `CAMERA_OCCLUDED` / `DRIVER_DEACTIVATED` |

**车队告警推送（fleet/{fleetId}/alert/push）— L3AlertEvent**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `fleetId` | string | ✓ | 车队 ID |
| `driverId` | string | ✓ | 驾驶员 ID |
| `vehicleId` | string | ✓ | 车辆 ID |
| `alertType` | string (enum) | ✓ | `FATIGUE` / `DISTRACTION` / `ROAD_RAGE` / `LIFE_DETECTION` / `COLLISION_DISABILITY` |
| `riskLevel` | string | ✓ | 固定 `L3_CRITICAL` |
| `occurredAt` | string (date-time) | ✓ | 告警触发时间 |
| `gpsLocation` | object | | `{ latitude, longitude }` |

**绩效预警推送（fleet/{fleetId}/performance-warning/push）— PerformanceWarningEvent**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `driverId` | string | ✓ | 驾驶员 ID |
| `driverName` | string | ✓ | 驾驶员姓名 |
| `fleetId` | string | ✓ | 车队 ID |
| `score` | number | ✓ | 绩效评分 (0–100) |
| `scorePeriod` | string (enum) | ✓ | `trip` / `weekly` / `monthly` / `quarterly` |
| `primaryPenaltyItems` | string[] | ✓ | 主要扣分项描述 |
| `occurredAt` | string (date-time) | ✓ | 预警触发时间 |

**SOS 确认通知（app/{accountId}/rescue/confirm）**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `rescueReportId` | string | ✓ | SOS 报告 ID |
| `status` | string (enum) | ✓ | `CONFIRMED` / `PENDING_RETRY` / `MANUAL_ESCALATION` |
| `confirmedAt` | string (date-time) | | 确认时间（status=CONFIRMED 时） |
| `message` | string | | 可读状态消息 |

### 2.3 QoS 等级策略

| QoS | 适用场景 | 说明 |
|:--:|------|------|
| **QoS 0** | 心跳 | 周期性上报，丢失单次不影响安全判定，下一周期自然覆盖 |
| **QoS 1** | 感知数据、告警、干预指令、车窗控制、OTA控制、家属推送 | 保证 at-least-once 送达，MQTT Broker 保证至少一次投递，消费方按消息幂等键去重 |

> 安全攸关的判定→干预链路在边缘侧本地同步完成（不依赖 MQTT 往返），MQTT 上报用于云端同步与远程监护，因此 QoS 1 满足需求。无需 QoS 2（QoS 2 的"正好一次"语义在 IoTDA 端并非严格保证，且额外往返延迟不适合实时告警场景）。

---

## 三、WebSocket / SparkRTC 集成

### 3.1 家属 APP WebSocket 信令协议

家属 APP（HarmonyOS）通过 WebSocket 与云端建立长连接，用于实时状态订阅、告警推送、音视频对讲信令。

**连接端点**：`wss://api.example.com/ws/guardianship?token=<JWT>`

**连接建立流程**：
1. 家属 APP 发起 WebSocket 握手，URL 携带 JWT token
2. 云端校验 token 有效性与 AccountRole（必须为 `FAMILY`），通过后升级为 WebSocket
3. 服务端下发 `connection_established` 帧，携带连接 ID
4. 一个家属账户最多 1 条活跃连接；重复连接时旧连接被替代

#### 上行消息（APP → 云端）

| 消息类型 | type 字段 | Payload | 说明 |
|---------|----------|---------|------|
| **订阅状态** | `subscribe_status` | `{ "driverId": "driver-007" }` | 订阅指定驾驶员的实时状态快照（≥1Hz），服务端回复 `subscribe_status_ack` |
| **取消订阅** | `unsubscribe_status` | `{ "subscriptionId": "sub-uuid" }` | 取消状态推送 |
| **心跳 PONG** | `pong` | `{}` | 响应服务端 PING |
| **发起对讲/视频** | `request_media` | `RequestMediaSessionRequest`（JSON，同 REST §1.3） | 发起音视频对讲请求 |
| **挂断对讲** | `end_media` | `{ "sessionHandle": "..." }` | 主动挂断 |
| **触发手动救援** | `trigger_rescue` | `TriggerManualRescueRequest`（JSON） | 一键手动救援触发 |

#### 下行消息（云端 → APP）

| 消息类型 | type 字段 | Payload | 说明 |
|---------|----------|---------|------|
| **连接建立** | `connection_established` | `{ "connectionId": "...", "accountId": "..." }` | 连接建立成功 |
| **心跳 PING** | `ping` | `{ "serverTime": "..." }` | 每 30s 发送，APP 须 10s 内回复 PONG |
| **状态快照推送** | `driver_status_snapshot` | `DriverStatusSnapshot`（JSON，同 §2.2） | ≥1Hz 周期推送 |
| **告警推送** | `alert_triggered` | `AlertSummary`（JSON） | 按家属 NotificationPreference 过滤后推送 |
| **权限授予通知** | `access_granted` | `{ "driverId": "...", "sessionToken": "...", "sparkRTCRoomId": "...", "sparkRTCJoinToken": "..." }` | 家属获得对讲/视频权限（常规 60s 授予或高危自动激活），附带 SparkRTC 入房凭证 |
| **权限撤销通知** | `access_revoked` | `{ "driverId": "...", "reason": "RISK_DECLINED" }` | 家属权限被撤销（reason: RISK_DECLINED / CAMERA_OCCLUDED / DRIVER_DEACTIVATED），APP 应断开对讲/视频 |
| **订阅确认** | `subscribe_status_ack` | `{ "subscriptionId": "...", "initialSnapshot": {...} }` | 订阅确认，附带即时状态快照 |
| **救援触发确认** | `rescue_triggered` | `{ "rescueRequestId": "...", "status": "..." }` | 手动救援触发确认 |
| **错误** | `error` | `{ "code": "...", "message": "..." }` | 操作失败 |

#### 心跳与重连策略

| 参数 | 值 |
|------|-----|
| 心跳间隔 | 30s PING → 10s 内须回复 PONG |
| 心跳超时 | 连续 3 次未收到 PONG → 视为断开 → 清除连接映射 + 释放推送流 |
| 重连退避 | 1s → 2s → 4s → 8s → 16s，最多 5 次 |
| 重连恢复 | 自动恢复所有有效订阅 + 补推断开期间最近一条告警摘要 |
| 离线消息 | 告警存储于离线队列（保留 7 天，AES-256-GCM 加密），重连后按时间倒序补推最多 20 条；补推按当前有效授权范围过滤；已撤销监护权限的家属不补推撤销前未投递告警 |
| 连接数限制 | 单个驾驶员最多 3 个家属同时订阅 |

### 3.2 SparkRTC 房间管理接口

家属 APP 与车机端之间的实时音视频对讲基于华为云 SparkRTC 实现。云端作为信令中继管理房间生命周期。

#### 房间管理流程

```
家属 APP                      云端 (S3)                     车机端 HMI
    │                             │                               │
    │ WebSocket: request_media     │                               │
    │  (sessionType=VIDEO)        │                               │
    │────────────────────────────►│                               │
    │                             │ 校验角色 + 二次身份验证         │
    │                             │ 校验 PermissionService          │
    │                             │                               │
    │                             │ 创建 SparkRTC 房间              │
    │                             │ (sparkRTCRoomId)               │
    │                             │                               │
    │                             │ MQTT: cmd/media/join/down      │
    │                             │──────────────────────────────►│
    │                             │         (sparkRTCRoomId,       │
    │                             │          sparkRTCJoinToken)    │
    │                             │                               │
    │                             │        MQTT Ack               │
    │                             │◄──────────────────────────────│
    │                             │                               │
    │ WebSocket: access_granted   │  HMI 声光提示"对讲已接通"       │
    │  (sparkRTCRoomId,           │                               │
    │   sparkRTCJoinToken)        │                               │
    │◄────────────────────────────│                               │
    │                             │                               │
    │ ════════ SparkRTC 音视频流直接对等传输 ════════               │
    │◄══════════════════════════════════════════════════════════►│
    │                             │                               │
    │ WebSocket: end_media         │                               │
    │────────────────────────────►│                               │
    │                             │ 销毁 SparkRTC 房间              │
    │                             │ 释放资源                        │
```

#### SparkRTC 房间参数

| 参数 | 说明 |
|------|------|
| 房间 ID 格式 | `room-{driverId}-{timestamp}`，如 `room-driver007-20260629083000` |
| 最大会话时长 | 10 分钟（超时自动挂断），高危失能场景下豁免此限制 |
| 视频编码 | H.264, 分辨率自适应（CIF/VGA/720P），默认低码率模式（≤500kbps），家属可请求提升清晰度 |
| 音频编码 | Opus 48kHz, 32kbps |
| 端到端加密 | SRTP (Secure RTP)，密钥经信令通道交换 |
| 云端留存 | 禁止——BR-04 隐私边界：音视频流端到端加密，云端不存留原始录像 |
| 驾驶员物理遮挡权 | 驾驶员在车内可通过 HMI 一键断开视频流（仅断开视频保留音频，或完全挂断），触发 CameraOcclusionDetectedEvent → 权限临时撤销 |

#### SparkRTC Token 签发

```
POST /api/v1/sparkrtc/token
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "roomId": "room-driver007-20260629083000",
  "userId": "family-account-042",
  "role": "subscriber"  // subscriber (家属端) / publisher (车机端)
}
→
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-06-29T08:40:00Z"
}
```

Token 由云端应用服务签发，后端集成华为云 SparkRTC SDK 的 Token 生成 API，以服务端密钥（appId + appKey）签名。Token 有效期 10 分钟，与房间最大会话时长一致。

---

## 四、ArkTS 前端对接契约

### 4.1 家属 APP（HarmonyOS）对接接口清单

家属 APP 基于 ArkTS 开发，运行于 HarmonyOS 设备。下为其调用的全部后端接口清单与数据模型。

#### REST API 调用列表

| 功能 | 方法 | 路径 | 请求体/模型 | 响应体/模型 |
|------|------|------|---------|---------|
| 查询驾驶员风险状态 | GET | `/api/v1/drivers/{driverId}/risk-status` | — | `GetDriverRiskStatusResponse` |
| 更新通知偏好 | PUT | `/api/v1/guardianship/notification-preference` | `UpdateNotificationPreferenceReq` | — |
| 一键救援触发 | POST | `/api/v1/guardianship/manual-rescue` | `TriggerManualRescueReq` | `TriggerManualRescueResp` |
| 远程车窗控制 | POST | `/api/v1/guardianship/window-control` | `ControlVehicleWindowReq` | — |
| 查询车窗状态 | GET | `/api/v1/vehicles/{vehicleId}/windows` | — | `QueryWindowStatusResp` |
| 查询家属监护权限 | GET | `/api/v1/guardianship/{driverId}/permissions` | — | `QueryGuardianshipPermissionsResp` |

#### ArkTS 数据模型定义（DTO）

```typescript
// === GetDriverRiskStatusResponse ===
interface GetDriverRiskStatusResponse {
  hasActiveTrip: boolean
  activeAlerts: Array<ActiveAlertEntry>
  derivedStatusColor: StatusColor          // 'GREEN' | 'YELLOW' | 'RED'
}

interface ActiveAlertEntry {
  alertType: AlertType                     // 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY'
  riskLevel: RiskLevel                     // 'L2_WARNING' | 'L3_CRITICAL'
}

// === UpdateNotificationPreferenceReq ===
interface UpdateNotificationPreferenceReq {
  familyAccountId: string
  driverId: string
  preferredRiskLevels: Array<RiskLevel>    // 空数组 = 接收全部
}

// === TriggerManualRescueReq / Resp ===
interface TriggerManualRescueReq {
  familyAccountId: string
  driverId: string
  secondaryAuthToken: string
}

interface TriggerManualRescueResp {
  rescueRequestId: string
  status: RescueRequestStatus             // 'PENDING' | 'CONFIRMED' | 'REJECTED'
}

// === ControlVehicleWindowReq ===
interface ControlVehicleWindowReq {
  familyAccountId: string
  driverId: string
  windowOperation: WindowControlOperation  // 'OPEN' | 'CLOSE' | 'PARTIAL_OPEN'
  windowPosition: WindowPosition           // 'FRONT_LEFT' | 'FRONT_RIGHT' | 'REAR_LEFT' | 'REAR_RIGHT'
  secondaryAuthToken: string
}

// === QueryWindowStatusResp ===
interface QueryWindowStatusResp {
  windowStatuses: Array<WindowStatusEntry>
}

interface WindowStatusEntry {
  windowPosition: WindowPosition
  state: WindowState                       // 'OPEN' | 'CLOSED' | 'PARTIAL' | 'UNKNOWN' | 'MOVING'
  lastOperation?: WindowControlOperation
  lastOperationResult?: WindowOperationResult  // 'SUCCESS' | 'TIMEOUT' | 'FAILED' | 'PENDING'
  updatedAt: string                        // ISO 8601
}

// === QueryGuardianshipPermissionsResp ===
interface QueryGuardianshipPermissionsResp {
  familyAccountId: string
  driverId: string
  permissions: Array<GuardianshipPermissionEntry>
  careRelationship: CareRelationshipSummary
}

interface GuardianshipPermissionEntry {
  permissionType: GuardianshipPermissionType  // 'MEDIA_CALL' | 'WINDOW_CONTROL' | 'MANUAL_RESCUE' | 'STATUS_MONITORING'
  granted: boolean
  grantedAt: string
  expiresAt?: string
}

interface CareRelationshipSummary {
  status: CareRelationshipStatus            // 'ACTIVE' | 'SUSPENDED' | 'REVOKED'
  establishedAt: string
}

// === WebSocket 下行消息模型 ===
interface DriverStatusSnapshot {
  driverId: string
  vehicleId: string
  timestamp: string
  activeAlertLevels: Record<AlertType, RiskLevel>
  gpsLocation?: GeoPoint
  speed?: number
  tripStatus: TripStatus                   // 'NOT_STARTED' | 'ACTIVE' | 'COMPLETED'
  physiologicalSummary?: PhysiologicalDigest
  windowStatus?: Array<WindowStatusEntry>
}

interface GeoPoint {
  latitude: number
  longitude: number
}

interface PhysiologicalDigest {
  heartRate: number
  spo2: number
  emotionIndex: number
}

// === 告警推送消息模型 ===
interface AlertSummary {
  alertId: string
  alertType: AlertType
  riskLevel: RiskLevel
  occurredAt: string
  resolvedAt?: string
  tripId: string
  gpsLocation?: GeoPoint
}

// === 权限授予/撤销消息模型 ===
interface AccessGrantedMessage {
  driverId: string
  sessionToken: string
  sparkRTCRoomId: string
  sparkRTCJoinToken: string
  reason: AccessGrantReason                // 'REGULAR_60S' | 'EMERGENCY_ACTIVATION' | 'OCCLUSION_RECOVERY'
}

interface AccessRevokedMessage {
  driverId: string
  reason: AccessRevokeReason               // 'RISK_DECLINED' | 'CAMERA_OCCLUDED' | 'DRIVER_DEACTIVATED'
}
```

#### 家属 APP WebSocket 连接管理

```typescript
// 连接管理
const ws = new WebSocket('wss://api.example.com/ws/guardianship?token=<JWT>')

// 心跳
ws.on('ping', (msg: { serverTime: string }) => {
  ws.send(JSON.stringify({ type: 'pong' }))
})

// 状态快照订阅
ws.send(JSON.stringify({
  type: 'subscribe_status',
  payload: { driverId: 'driver-007' }
}))

// 告警处理
ws.on('alert_triggered', (alert: AlertSummary) => {
  // 按 alertType 和 riskLevel 展示差异化告警 UI
  // L3_CRITICAL 时触发高频振动 + 红色全屏通知
})

// 自动激活接入（高危失能）
ws.on('access_granted', (msg: AccessGrantedMessage) => {
  if (msg.reason === 'EMERGENCY_ACTIVATION') {
    // 自动接入 SparkRTC 房间：使用 sparkRTCJoinToken 加入
    sparkRTCClient.joinRoom(msg.sparkRTCRoomId, msg.sparkRTCJoinToken)
  }
})

// 权限撤销
ws.on('access_revoked', (msg: AccessRevokedMessage) => {
  sparkRTCClient.leaveRoom()
  // UI 提示权限已撤销及原因
})
```

### 4.2 车队大屏对接

车队大屏以 Web 应用形式部署（也可基于 ArkTS 开发），消费以下接口：

#### 看板数据订阅模型（WebSocket + REST 混合）

| 数据流 | 方式 | 端点/Topic | 频率 |
|------|:--:|------|------|
| 疲劳分布看板 | REST GET（带缓存） | `/api/v1/fleet/{fleetId}/fatigue-distribution` | 默认 5min 周期，支持手动刷新 |
| 脱线车辆列表 | REST GET | `/api/v1/fleet/{fleetId}/offline-vehicles` | 事件驱动（SensorFailureEvent 3s 内反映） |
| L3 高危告警实时推送 | WebSocket 订阅 | `ws://api.example.com/ws/fleet?token=<JWT>` | 事件驱动（AlertTriggeredEvent L3） |
| 绩效预警推送 | WebSocket 订阅 | 同上连接 | 事件驱动（PerformanceWarningEvent） |
| 高风险司机钻取 | REST GET | `/api/v1/fleet/{fleetId}/high-risk-drivers` | 按需（点击触发） |
| 轨迹查询 | REST GET | `/api/v1/fleet/{fleetId}/trajectory` | 按需 |
| 驾驶行为报告 | REST POST + GET | `/api/v1/fleet/reports` + `/download` | 按需（≤15s SLA） |

#### 车队大屏 WebSocket 消息模型

```typescript
// 大屏 WebSocket 连接
const ws = new WebSocket('wss://api.example.com/ws/fleet?token=<JWT>')

// L3 高危告警推送
ws.on('l3_alert', (msg: L3AlertMessage) => {
  // 使对应车队的高危车辆列表缓存失效 + 前端即时刷新
})

interface L3AlertMessage {
  fleetId: string
  driverId: string
  vehicleId: string
  alertType: AlertType
  occurredAt: string
  gpsLocation: GeoPoint
}

// 绩效预警推送
ws.on('performance_warning', (msg: PerformanceWarningMessage) => {
  // 弹窗通知管理员
})

interface PerformanceWarningMessage {
  driverId: string
  driverName: string
  score: number
  scorePeriod: string          // 'trip' | 'weekly' | 'monthly' | 'quarterly'
  primaryPenaltyItems: Array<string>
  occurredAt: string
}
```

### 4.3 HMI（车机端）本地查询接口

车机端 HMI 运行于边缘侧终端，与边缘侧的 S1、S2 应用服务共享进程，通过进程内调用（非 REST）查询：

| 功能 | 调用方式 | 接口 | 说明 |
|------|------|------|------|
| 当前干预指令集合 | 进程内（Java 方法调用） | `S2.queryInterventionStatus(tripId)` → `QueryInterventionStatusResponse` | HMI 按指令渲染氛围灯/语音/震动等 |
| 当前传感器自检状态 | 进程内 | 读取 Vehicle 聚合的 SensorStatus 集合 | 异常时展示"传感器故障，请谨慎驾驶" |
| 摄像头遮挡提示 | 进程内事件 | 消费 `CameraOcclusionDetectedEvent` / `CameraOcclusionRemovedEvent` | 展示/撤销遮挡提示 |
| 家属对讲/视频接通声光提示 | 进程内事件 | 消费 `FamilyAccessGrantedEvent` | "远程对讲/视频已接通" HMI 提示 |
| 驾驶员物理遮挡权执行 | 进程内调用 | 触发摄像头画面遮挡 → 产生 `CameraOcclusionDetectedEvent` | 驾驶员通过 HMI 一键断开视频 |
| 驾驶员覆盖信号（Override） | 进程内调用 | `S2.reportOverride(OverrideSignal)` | HMI 检测转向/制动/加速操作后上报 |
| 后排红外影像画中画 | 进程内 | 直接读取红外摄像头视频流 | 不流经融合判定门面 |

---

## 五、安全设计

### 5.1 API 认证——JWT / OAuth2

#### JWT Token 结构

```
Header: { "alg": "RS256", "typ": "JWT" }
Payload: {
  "sub": "account-042",           // 账户 ID
  "role": "FAMILY",               // AccountRole: FAMILY / MANAGER / RESCUE
  "iat": 1719649800,              // 签发时间
  "exp": 1719653400,              // 过期时间（默认 1h）
  "scope": ["read:risk-status", "write:window-control"]
}
Signature: RSA-SHA256 签名
```

#### 签发与校验流程

1. 用户登录后，云端认证服务（IAM）校验用户名/密码或手机验证码，通过后签发 JWT
2. JWT 以 RS256（非对称密钥）签名，私钥由认证服务持有，公钥分发至各微服务用于校验
3. API 网关（APIG）在请求到达应用服务前统一校验 JWT 有效性（签名 + 过期时间）
4. 应用服务从 JWT 中提取 `sub`（账户 ID）和 `role`，用于后续安全门控（角色校验 + 权限判定）
5. Token 刷新：JWT 过期前 5 分钟，客户端可携带 refresh token 换取新 JWT

#### 角色 → 权限映射

| 角色 | 可访问端点范围 | 说明 |
|------|------|------|
| `FAMILY` | S1 风险状态查询、S3 全部端点（对讲/视频/车窗/救援）、家属 WebSocket 推送 | 仅可查询/操作自己所监护的驾驶员 |
| `MANAGER` | S1 告警历史查询、S2 干预历史查询、S4 全部端点、S6 OTA 管理、车队大屏 WebSocket | 全局车队统计权限 |
| `RESCUE` | S5 全部端点（SOS 确认/授权凭证签发与消费/救援历史） | 限定救援机构操作 |

### 5.2 二次身份验证（高敏操作门控）

以下操作在执行前必须完成二次身份验证：

| 操作 | 二次验证方式 | 门控位置 |
|------|------------|---------|
| 家属发起远程对讲 | 生物特征（指纹/人脸）或动态短信 OTP | S3 `requestMediaSession` 入口 |
| 家属发起远程视频监控 | 同上 | S3 `requestMediaSession` 入口 |
| 家属远程车窗控制 | 同上 | S3 `controlVehicleWindow` 入口 |
| 家属手动救援触发 | 同上 | S3 `triggerManualRescue` 入口 |
| 救援机构远程车门解锁 | 救援授权凭证（RescueAuthorizationToken） + 角色校验（AccountRole=RESCUE） | S5 `verifyRescueToken` 入口 |

> **自动激活豁免**：BR-06 碰撞失能等高危场景下，家属端对讲/视频自动激活接入——由系统侧基于场景判定自动触发，豁免家属手动二次验证发起，但仍须经系统侧场景有效性校验（见需求 BR-07 的特例补充）。

#### 二次验证 Token 流程

```
家属 APP → 云端认证服务：
  POST /api/v1/auth/secondary-verify
  { "accountId": "...", "method": "OTP", "otp": "123456" }
→
  { "secondaryAuthToken": "eyJ...2fa", "expiresAt": "..." }  // 有效期 5 分钟
```

后续高敏操作携带 `secondaryAuthToken` 字段，应用层校验其有效性和时效性。

### 5.3 接口限流策略

采用**令牌桶**算法，按角色和端点分级限流：

| 限流层级 | 限制规格 | 适用端点 |
|:--:|------|------|
| **全局** | 1000 req/s / 单个 API 网关实例 | 全部 REST 端点 |
| **家属角色** | 30 req/s / 账户 | S3 全部端点 + WebSocket 消息 |
| **管理员角色** | 60 req/s / 账户 | S4 看板/报告/钻取 |
| **救援机构角色** | 20 req/s / 账户 | S5 全部端点 |
| **高敏操作** | 3 req/min / 账户 | 对讲建立、车窗控制、远程解锁、手动救援 |
| **报告生成** | 5 req/min / 管理员 | S4 `generateReport`（消耗数据库与 CPU）
| **MQTT 上报** | IoTDA 设备级限速：100 msg/s / 设备 | 设备→云端感知数据上报（超过则 IoTDA 侧拒绝） |

> 令牌桶参数可动态配置（Nacos/配置中心），突发容忍度为桶容量的 1.5 倍。超限时返回 HTTP `429 Too Many Requests`，响应头 `Retry-After` 指示等待秒数。家属 APP 收到 429 后执行退避重试。

### 5.4 MQTT 设备鉴权

边缘侧终端接入华为云 IoTDA 的设备鉴权采用**X.509 证书**方案：

| 鉴权要素 | 配置 |
|------|------|
| 认证方式 | X.509 客户端证书（设备证书 + CA 证书链） |
| 证书签发 | 华为云 IoTDA 设备管理控制台生成或企业 CA 签发 |
| 设备标识 | 证书 CN 字段 = `{deviceId}`（与 MQTT Topic 中 `{deviceId}` 一致） |
| 证书生命周期 | 有效期 5 年，到期前 30 天云端推送新证书（OTA 下发） |
| 吊销 | IoTDA 控制台手动吊销，或通过 CRL（证书吊销列表）自动分发 |
| 备选方案 | 对不支持 X.509 的轻量设备，使用设备密钥（DeviceSecret）认证——一机一密 |
| 连接安全 | TLS 1.2+，密码套件 `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` |

#### IoTDA 设备注册流程

1. 车辆出厂时，边缘侧终端预置唯一设备 ID（终端序列号 / VIN 哈希）和初始设备密钥
2. 首次上电连接 IoTDA 时，设备以预置密钥认证并请求下发 X.509 证书
3. IoTDA 为该设备签发 X.509 证书（含根 CA 签名），经 MQTT 下发至终端
4. 终端存储证书于安全区域（TPM/安全文件系统），后续以证书认证连接

### 5.5 敏感数据传输加密策略

| 数据类别 | 传输层加密 | 应用层加密 | 存储加密 | 说明 |
|------|:--:|:--:|:--:|------|
| **REST API 请求/响应** | TLS 1.2+ (HTTPS) | — | — | 全链路 HTTPS，HSTS 强制 |
| **MQTT 设备-云通信** | TLS 1.2+ (MQTTS) | — | 云端 OBS 服务端加密 | IoTDA MQTT over TLS |
| **WebSocket 连接** | TLS 1.2+ (WSS) | — | — | 家属 APP / 车队大屏长连接 |
| **SparkRTC 音视频流** | SRTP (DTLS-SRTP) | — | 禁止云端留存原始录像 | 端到端加密，信令面密钥通过 WSS 交换 |
| **DMS 原始图像** | — | 边缘侧脱敏（人脸关键点提取/模糊化）→ 仅传输脱敏特征向量 | 原始图像仅缓存在边缘终端内存，不落盘、不上云 |
| **路怒语音存证** | — | AES-256-GCM 加密 → 经 MQTT 上传 | 云端 OBS 加密桶，保留 90 天自动删除 | 音频片段独立加密，密钥由 KMS 管理 |
| **驾驶员健康档案** | TLS 1.2+ | 字段级加密（血型等敏感字段） | 数据库列加密 | 仅 SOS/救援授权下可解密调取 |
| **JWT Token** | TLS 1.2+ | RS256 签名 + 加密载荷 | — | 不存储于客户端 localStorage，使用安全 Cookie 或 KeyStore |
| **MQTT Payload** | TLS 1.2+ | 可选 Payload 加密（AES-GCM） | 云端不解析加密载荷内容 | 仅在需云端做内容路由/判定时不加密；仅存储/中转时加密 |

#### 密钥管理

- 云端密钥管理服务（华为云 KMS）负责所有对称加密密钥（AES-256-GCM）的生成、轮换和访问控制
- JWT 签名密钥由 KMS 托管，私钥不出 KMS，签名操作通过 KMS API 完成
- MQTT X.509 证书的私钥由边缘终端 TPM/安全文件系统保护，不可导出
- 密钥轮换周期：数据加密密钥每季度自动轮换，旧密钥保留 90 天供解密历史数据；JWT 签名密钥每年轮换

### 5.6 隐私边界安全校验点

遵循 BR-04 隐私数据边界，在 API 层设置以下安全校验点：

| 校验点 | 位置 | 规则 |
|------|------|------|
| DMS 原始图像上云拦截 | 边缘侧 MQTT 上报前（PrivacyProtectionService） | `validateDataDesensitization` 校验：特征向量标签 ≠ 原始图像 → 允许上云；否则阻断并审计 |
| 家属查询驾驶员位置 | S3 `subscribeDriverStatus` 入口 | 仅当家属持有有效监护关系 + 权限通过时返回 GPS，无权限时 GPS 字段为 `null` |
| 家属调取路怒语音存证 | 审计接口（需二次授权） | 仅限交通事故定责 / 投诉受理 / 司法协查三类场景，经 PermissionService 二次授权后解密访问 |
| 车队管理员查询历史告警 | S1 `queryAlertHistory` + S4 | 返回脱敏数据（含告警类型/等级/时间，不含原始图像/语音），按 CQRS 读模型投影 |
| 音视频流云端留存 | SparkRTC 配置 | 强制禁用云端录制；仅在家属端本地可缓存（由家属 APP 控制），且 BR-04 要求所有原始视频禁止上云 |
| 驾驶员注销后数据清理 | DriverDeactivatedEvent 触发 | 隐私敏感数据（DriverHealthProfile、RoadRageVoiceRecord）删除或脱敏；历史行程数据匿名化保留（仅保留聚合统计价值） |
| 家属查询监护权限 | S3 权限查询入口 (`GET /api/v1/guardianship/{driverId}/permissions`) | 仅返回与请求方 accountId 关联的监护权限；拒绝查询非本人持有的权限关系 |

---

## 修订说明（v2）

以下变更基于组件B诊断报告（b_v1_diag_v1.md）的 6 个 LOCATED 问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1：S3 缺失家属权限查询 REST 端点 | **修改** — 在 §1.3 REST 端点表新增 `GET /api/v1/guardianship/{driverId}/permissions` 端点及 `QueryGuardianshipPermissionsResponse` 模型，映射到应用层 `IRemoteGuardianshipService` 新增查询方法 |
| 问题 2：MQTT Payload JSON Schema 覆盖不完整 | **修改** — 在 §2.2 补充 14 个 Payload 定义：车窗控制指令、车门解锁指令、OTA 升级指令、OTA 回滚指令、指令 Ack、传感器故障、摄像头遮挡、心跳、行程状态、生理体征快照、车辆状态遥测、驾驶员覆盖信号、行程评分、路怒语音存证（核心 Topic 提供完整 JSON Schema；家属告警推送/权限授予/撤销/车队告警/绩效预警/SOS 确认 6 个推送消息以表格形式提供字段级定义） |
| 问题 3：SparkRTC Token 端点未归属到应用服务 | **修改** — 将 `POST /api/v1/sparkrtc/token` 纳入 §1.3 S3 REST 端点表，新增 `IssueSparkRTCTokenRequest` / `IssueSparkRTCTokenResponse` 模型，明确归属 S3 RemoteGuardianshipService |
| 问题 4：QueryAlertHistoryResponse 缺失 GPS 字段 | **修改** — 在 §1.1 QueryAlertHistoryResponse 的 AlertSummary 中补充 `gpsLocation` 可选字段（含 `latitude`、`longitude`），与 MQTT SafetyAlertEvent 的 `gps` 字段保持一致 |
| 问题 5：OTA 升级管理缺少取消未启动任务的 REST 端点 | **修改** — 在 §1.6 S6 REST 端点表新增 `DELETE /api/v1/ota/upgrade-tasks/{taskId}` 端点及 `CancelUpgradeTaskResponse` 模型；补充错误响应 `UpgradeTaskNotCancellable`（仅 PENDING/TRANSMITTING 阶段可取消） |
| 问题 6：安全隐私校验点表缺少家属权限查询的隐私保护规则 | **修改** — 在 §5.6 表中新增一行：`家属查询监护权限 | S3 权限查询入口 | 仅返回与请求方 accountId 关联的监护权限；拒绝查询非本人持有的权限关系` |

