# 车载安全监测系统 API/接口层 OOD 设计方案（a_v12 / v12）

> 本文档为「智能物联——基于多传感器融合的车载安全监测系统」的**接口/API 层**架构级 OOD 设计方案，承接领域层 OOD（`docs/ood_domain.md`）与应用层 OOD（`docs/ood_application.md`）。本文档定义六个应用服务对外暴露的完整 API 契约（REST/MQTT/WebSocket）、MQTT 设备-云通信主题路由、家属 APP WebSocket/SparkRTC 信令集成、ArkTS 前端对接数据模型，以及全链路安全设计。
> 技术栈：前端 ArkTS（HarmonyOS），后端 Java Spring Boot，设备-云通信基于华为云 IoTDA（MQTT），实时音视频基于华为云 SparkRTC。

---

## 一、REST API 契约

所有 API 端点均遵循 RESTful 风格，按 OpenAPI 3.0 风格描述。认证头统一为 `Authorization: Bearer <JWT>`。基础路径：`/api/v1`。

> **DELETE 响应码约定**：本设计采用混合策略——无响应体的端点（如删除音视频会话，无可返回的业务状态）返回 `204 No Content`；需返回操作结果或状态变更信息的端点（如取消升级任务需返回取消前后状态）返回 `200 OK` 并携带响应体。两者均符合 REST 语义，选择取决于业务是否需要向调用方反馈操作结果详情。

> **401 统一处理约定**：各端点的 JWT 有效性校验（签名、过期时间）由 API 网关（APIG）在请求到达应用服务前统一完成。因 JWT 缺失、无效或过期导致的 `401 Unauthorized` 由网关统一返回，各端点错误响应中不单独列出。仅当端点存在应用层特定鉴权逻辑导致的 401（如 §1.3 二次身份验证未通过 `SecondaryAuthRequired`）时该端点单独标注。

> **REST 错误响应体约定**：所有 REST 端点返回非 2xx 状态码时，统一使用以下响应体格式：
> ```json
> {
>   "errorCode": "BatchSizeExceeded",
>   "message": "批量超限，最大支持 100 辆车",
>   "requestId": "trace-uuid"
> }
> ```
> - `errorCode`：映射到应用层 `AppError` 枚举变体名（见 `docs/ood_application.md` §6.1），取值如 `BatchSizeExceeded`、`SecondaryAuthRequired`、`PermissionDenied`、`SubscriptionLimitExceeded`、`SessionEstablishFailed`、`WindowControlTimeout`、`AccessDenied`、`ConcurrentConsumption`、`ReportGenerationTimeout`、`UpgradeTaskNotCancellable`、`IoTDAChannelFailure` 等
> - `message`：人类可读的错误描述，由后端填充具体原因
> - `requestId`：请求链路追踪 UUID，对应网关注入的 `X-Request-ID` 头，用于问题排查和日志关联
>
> 本文档各节错误响应块中标注的 HTTP 状态码和对应的 `AppError` 枚举值均遵循此格式。REST 客户端应统一解析 `errorCode` 字段（而非仅依赖 HTTP 状态码）进行异常分支处理。

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

**activeAlerts** 为当前活跃风险及其等级的映射列表。`derivedStatusColor` 取值：`GREEN` / `YELLOW` / `RED`（无风险/L1 → GREEN, L2 → YELLOW, L3 → RED）。此三值枚举与领域层 VO-15 `StatusColor` 定义一致；若应用层引用额外值（如 `ORANGE`），应以领域层定义为准进行统一。

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

`gpsLocation` 为可选字段（含 `latitude`、`longitude`），与 MQTT SafetyAlertEvent 的 `gpsLocation` 字段保持一致。无 GPS 数据时该字段为 `null`。

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

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **查询当前干预状态** | GET | `/api/v1/trips/{tripId}/interventions/active` | — | — | `QueryInterventionStatusResponse` | 200 | JWT |
| **查询干预历史** | GET | `/api/v1/trips/{tripId}/interventions/history` | — | `page`, `size`, `startTime`, `endTime` | `QueryInterventionHistoryResponse` | 200 | JWT |

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

**错误响应**：
- `400` — 参数无效（如 `page`/`size` 超限或时间参数格式错误）
- `404` — `tripId` 不存在
- `503` — 服务不可用

> **S2 `404` 设计理由**：查询干预状态/历史以 `tripId` 为路径参数。`tripId` 不存在时返回 `404`（而非 `200 OK` + 空集合），与 S1/S4 对不存在资源的处理保持一致，消费者可统一以 `404` 判定资源缺失，无需记忆两套规则。若未来有防止行程 ID 存在性信息泄露的安全需求，可通过始终返回 `200` + 空集合将存在性检查内化至服务端，届时再在错误响应中补充设计理由。

---

### 1.3 S3 RemoteGuardianshipService — 远程监护服务

对应应用层 `IRemoteGuardianshipService` / `RemoteGuardianshipServiceImpl`。

状态订阅与推送通过 WebSocket 实现（见 §3），以下为 REST 端点。

> **设计说明：「手动救援触发」端点归口 S3 而非 S5**：需求 `requirement.md:27` 将家属手动救援触发列入 S5 EmergencyRescueService，但本文档将 `POST /api/v1/guardianship/manual-rescue` 归入 S3 RemoteGuardianshipService。理由是：(1) 路径前缀 `guardianship` 表明该操作为家属监护行为，与 S3 的职责"家属远程监护"内聚；(2) 该端点的调用方是家属 APP（而非救援机构），触发后由 S3 内部编排调度救援流程（含 S5 的救援报告生成）；(3) S5 面向救援机构（角色 RESCUE），而家属手动救援不要求 RESCUE 角色认证。此调整遵循按职责内聚归口的设计原则。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **请求建立音视频对讲** | POST | `/api/v1/guardianship/media-session` | `RequestMediaSessionRequest` | — | `RequestMediaSessionResponse` | 201 | JWT |
| **终止音视频会话** | DELETE | `/api/v1/guardianship/media-session/{sessionHandle}` | — | — | — | 204 | JWT |
| **更新通知偏好** | PUT | `/api/v1/guardianship/notification-preference` | `UpdateNotificationPreferenceRequest` | — | — | 204 | JWT |
| **一键手动救援触发** | POST | `/api/v1/guardianship/manual-rescue` | `TriggerManualRescueRequest` | — | `TriggerManualRescueResponse` | 201 | JWT |
| **远程车窗控制** | POST | `/api/v1/guardianship/window-control` | `ControlVehicleWindowRequest` | — | — | 202 | JWT |
| **查询车窗状态** | GET | `/api/v1/vehicles/{vehicleId}/windows` | — | — | `QueryWindowStatusResponse` | 200 | JWT |
| **查询家属监护权限** | GET | `/api/v1/guardianship/{driverId}/permissions` | — | — | `QueryGuardianshipPermissionsResponse` | 200 | JWT |
| **签发 SparkRTC Token** | POST | `/api/v1/sparkrtc/token` | `IssueSparkRTCTokenRequest` | — | `IssueSparkRTCTokenResponse` | 200 | JWT |

> **202 Accepted 说明**：`controlVehicleWindow` 返回 202（指令已下发至 IoTDA，不等同于车窗操作物理完成）。前端应进一步轮询 `queryWindowStatus` 确认执行结果。

> **应用层方法映射（v5 更新）**：以下新增端点对应应用层 `IRemoteGuardianshipService` 中已定义的方法（见 `docs/ood_application.md` §3.3 接口方法契约表及 §4.3 DTO 定义）：
> > - `GET /api/v1/guardianship/{driverId}/permissions` → `queryGuardianshipPermissions(driverId: String, accountId: String): QueryGuardianshipPermissionsResponse`
> >   - 入参：`driverId`（路径参数）+ `accountId`（从 JWT `sub` 提取，非请求体传入，见 §5.6 安全校验）
> >   - 返回：`QueryGuardianshipPermissionsResponse`（含 `permissions` 列表和 `careRelationship` 状态）
> > - `POST /api/v1/sparkrtc/token` → `issueSparkRTCToken(request: IssueSparkRTCTokenRequest): IssueSparkRTCTokenResponse`
> >   - 入参：`IssueSparkRTCTokenRequest`（含 `roomId`, `userId`, `role`）
> >   - 返回：`IssueSparkRTCTokenResponse`（含 `token`, `expiresAt`）

> **安全门控**：S3 的多个请求体包含 `familyAccountId` 字段（`RequestMediaSessionRequest`、`UpdateNotificationPreferenceRequest`、`TriggerManualRescueRequest`、`ControlVehicleWindowRequest`）。应用层入口必须从 JWT `sub` 声明中提取 `accountId` 并与请求体中的 `familyAccountId` 校验一致，不一致则拒绝请求（HTTP `403`）并记录安全审计日志。此校验规则同步纳入 §5.6 隐私边界校验表。

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
  "rescueReportId": "report-uuid",
  "status": "PENDING"
}
```

- `rescueRequestId`：家属手动救援触发请求的 ID（S3 内部）
- `rescueReportId`：对应的 S5 救援报告 ID。S3 在处理手动救援触发时，内部创建 S5 EmergencyRescueService 的救援记录（`rescueReport`），并将生成/关联的 `rescueReportId` 一并返回给家属 APP，消除 S3 `rescueRequestId` 与 S5 `rescueReportId` 之间的 ID 断裂。WebSocket 下行 `rescue_triggered` 消息同步携带此字段。
- `status`: `PENDING` | `CONFIRMED` | `REJECTED`

> **S3→S5 手动救援流转说明**：家属 APP 通过 `POST /api/v1/guardianship/manual-rescue` 触发手动救援后，S3 RemoteGuardianshipService 执行以下编排：(1) 生成 `rescueRequestId`；(2) 调用 S5 `IEmergencyRescueService.createRescueReport()` 创建救援报告并获取 `rescueReportId`；(3) 将 `rescueReportId` 与 `rescueRequestId` 关联存储在救援请求记录中；(4) 返回包含两个 ID 的 TriggerManualRescueResponse。后续家属可通过 S5 `queryRescueHistory` 以 `rescueReportId` 查询救援进展。
> 
> **接口契约已落地（v10）**：步骤 (2) 引用的 `IEmergencyRescueService.createRescueReport()` 方法已在本轮（v10）于 `docs/ood_application.md` §3.5 接口方法表中形式化定义（含完整方法签名、输入/输出 DTO `CreateRescueReportRequest`/`CreateRescueReportResponse`、事务属性、异常处理），对应 DTO 定义见 `docs/ood_application.md` §4.5。下游 S3 实现者可据此完整实现编排逻辑。
> 
> **状态枚举映射**：S3 `TriggerManualRescueResponse.status` 与 S5 `RescueRecordSummary.status` 使用不同枚举值表示同一语义状态。下游实现时请按以下映射转换：S3 `PENDING` ↔ S5 `SENT`（已触发，等待救援中心响应）；S3 `CONFIRMED` ↔ S5 `CONFIRMED`（救援中心已确认）；S3 `REJECTED` ↔ S5 `MANUAL_ESCALATION`（拒绝后转为人工升级）。S3 的枚举值为家属 APP 面向用户的即时状态，S5 的枚举值为救援记录的完整生命周期状态。

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
>
> **设计理由：「家属权限查询/管理」仅提供 GET 查询端点，不提供主动管理入口（DELETE/REVOKE）**：
> > 1. **授权模型单向性**：家属监护权限的授予（`access/granted`）由系统侧基于场景判定自动触发——常规 60s 授予由 S3 PermissionService 在 `CameraOcclusionDetectedEvent→CameraOcclusionRemovedEvent` 窗口内自动发放；高危失能场景由 S1 `EmergencyActivatedEvent` 驱动授予。家属端不主动发起权限申请，因此无需 PUT/POST 管理端点。
> > 2. **撤销驱动来源非家属侧**：权限撤销（`access/revoked`）由系统侧事件驱动（`RISK_DECLINED` / `CAMERA_OCCLUDED` / `DRIVER_DEACTIVATED`），或由驾驶员在 HMI 端通过物理遮挡权主动触发。家属侧主动撤销需求（如"我不想再监护该驾驶员"）不在需求描述范围内，且家属为监护权限的受益方而非授权方——授权决策权属于驾驶员（通过系统预授权配置体现）。
> > 3. **DELETE 端点语义不适合当前模型**：若为家属提供 DELETE 主动撤销端点（如 `DELETE /api/v1/guardianship/{driverId}/permissions`），其语义与系统侧授权模型冲突——家属撤销后系统侧是否应重新自动授予？驾驶员是否应被告知家属已主动放弃监护？这些交互语义需在需求层面澄清后方可设计。
> > 4. **合规与安全考量**：家属权限是隐私敏感资源（涉及驾驶员实时位置、生理体征、车内音视频）。若开放家属侧主动撤销，需配套撤销审计日志、撤销冷静期、驾驶员通知等机制，当前系统设计聚焦于安全监测核心功能，暂不引入此复杂度。
> >
> > 综上，当前设计仅提供 `GET /api/v1/guardianship/{driverId}/permissions` 查询家属当前权限状态，权限生命周期管理由系统侧自动化流程驱动。若未来需求明确要求家属主动撤销监护权限，可新增 `DELETE /api/v1/guardianship/{driverId}/permissions` 端点，并配套上述审计与通知机制。

#### IssueSparkRTCTokenRequest

```json
{
  "roomId": "room-driver007-20260629083000",
  "userId": "family-account-042",
  "role": "subscriber"
}
```

`role`: `subscriber`（家属端，REST 端点唯一合法取值）。`publisher` 角色用于车机端 Token，通过 MQTT `cmd/media/join/down` 下发至车机设备，不经此 REST 端点。前端传入 `publisher` 时后端拒绝该请求。

> **安全约束与天花板限制**：`POST /api/v1/sparkrtc/token` 端点按 §5.1 角色映射仅 FAMILY 角色可调用。本端点的 `role` 字段天花板限制为 `subscriber`——FAMILY 角色调用方**仅可请求 `role=subscriber`**，传 `publisher` 将返回 `400 Bad Request`（`InvalidRoleForEndpoint`）。后端须在请求体校验层先拒绝 `role=publisher`，再校验 JWT `AccountRole` 与请求 `role` 字段的一致性。车机端的 `publisher` Token 仅通过 MQTT `cmd/media/join/down` 下发，不经此 REST 端点。

#### IssueSparkRTCTokenResponse

```json
{
  "token": "eyJhbGciOi...",
  "expiresAt": "2026-06-29T08:40:00Z"
}
```

Token 有效期 10 分钟，与房间最大会话时长一致。由 S3 RemoteGuardianshipService 签发，后端集成华为云 SparkRTC SDK Token 生成 API。

**错误响应**：
- `400` — 请求参数无效（如 `role=publisher` 被 `POST /api/v1/sparkrtc/token` 拒绝、查询参数格式错误）
- `401` — 二次身份验证未通过（`SecondaryAuthRequired`）
- `403` — 权限不足（`PermissionDenied`，含具体原因：`NotRelated` / `NoAuthorization` / `AuthorizationExpired` / `AuthorizationRevoked`）
- `404` — 驾驶员/车辆不存在（如 `queryGuardianshipPermissions` 的 `driverId` 不存在、`queryWindowStatus` 的 `vehicleId` 不存在）
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
| **取消绩效预警订阅** | DELETE | `/api/v1/fleet/performance-warning-subscription/{subscriptionId}` | — | — | — | 204 | JWT |

> **下载响应头说明**：
> - `format=pdf` → `Content-Type: application/pdf`
> - `format=xlsx` → `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
> - 均附带 `Content-Disposition: attachment; filename="report-{reportId}.{format}"` 头，强制浏览器触发下载。

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
  "totalCount": 3600,
  "dataConsistency": "CONSISTENT"
}
```

- `dataConsistency`：`CONSISTENT` | `INCONSISTENT`。当 `vehicleId` 和 `driverId` 同时提供但不匹配时，返回 `INCONSISTENT` 且 `trajectoryPoints` 为空序列。
- 查询参数：`vehicleId` 和 `driverId` 至少提供一个。

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

#### SubscribePerformanceWarningRequest

```json
{
  "adminId": "admin-001",
  "fleetId": "fleet-042"
}
```

`adminId`：车队管理员账户 ID（须与 JWT `sub` 一致，由后端校验）；`fleetId`：目标车队 ID。

#### SubscribePerformanceWarningResponse

```json
{
  "subscriptionId": "sub-uuid"
}
```

绩效预警通过 WebSocket 主动推送至管理端（见 §3）。

**错误响应**：
- `400` — 参数无效（如 `vehicleId` 和 `driverId` 均未提供）
- `404` — 资源不存在（如指定的 `fleetId` 或 `reportId` 不存在）
- `504` — 报告生成超时（`AppError.ReportGenerationTimeout`，SLA 为 15 秒）

---

### 1.5 S5 EmergencyRescueService — 应急救援服务

对应应用层 `IEmergencyRescueService` / `EmergencyRescueServiceImpl`。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **SOS 报告确认** | POST | `/api/v1/emergency/sos-confirm` | `ConfirmSOSReportRequest` | — | — | 204 | JWT (RESCUE) |
| **签发救援授权凭证** | POST | `/api/v1/emergency/rescue-tokens` | `IssueRescueTokenRequest` | — | `IssueRescueTokenResponse` | 201 | JWT (RESCUE) |
| **校验并消费救援凭证** | POST | `/api/v1/emergency/rescue-tokens/verify` | `VerifyRescueTokenRequest` | — | `VerifyRescueTokenResponse` | 200 | JWT (RESCUE) |
| **查询救援历史** | GET | `/api/v1/emergency/rescue-history` | — | `driverId`, `vehicleId`, `startTime`, `endTime`, `page`, `size` | `QueryRescueHistoryResponse` | 200 | JWT (RESCUE) |

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

**错误响应**：
- `400` — 查询参数无效（如 `queryRescueHistory` 中 `driverId`/`vehicleId` 格式错误或分页参数超限）
- `403` — 授权凭证过期 / 已消费 / 角色不匹配 / 操作未授权（`AccessDenied`）
- `404` — 驾驶员/车辆不存在（`queryRescueHistory` 中指定的 `driverId`/`vehicleId` 在系统中不存在）
- `409` — 并发消费冲突（`ConcurrentConsumption`）

> **内部编排接口说明**：S5 的 `createRescueReport` 方法供 S3 RemoteGuardianshipService 在处理手动救援触发时内部编排调用（见 §1.3 S3→S5 手动救援流转说明），不对外暴露为独立的 REST 端点。该方法的方法签名和输入/输出 DTO 定义见 `docs/ood_application.md` §3.5 接口方法表（`createRescueReport` 行）及 §4.5 DTO（`CreateRescueReportRequest`/`CreateRescueReportResponse`）。

---

### 1.6 S6 OTAManagementService — OTA 升级管理服务

对应应用层 `IOTAManagementService` / `OTAManagementServiceImpl`。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **创建升级任务** | POST | `/api/v1/ota/upgrade-tasks` | `CreateUpgradeTaskRequest` | — | `CreateUpgradeTaskResponse` | 201 | JWT |
| **批量查询升级进度** | GET | `/api/v1/ota/upgrade-progress` | — | `vehicleIds` (comma-separated, required) | `QueryUpgradeProgressResponse` | 200 | JWT |
| **触发回滚** | POST | `/api/v1/ota/rollback` | `TriggerRollbackRequest` | — | `TriggerRollbackResponse` | 200 | JWT |
| **查询升级历史** | GET | `/api/v1/ota/upgrade-history/{vehicleId}` | — | `page`, `size` | `QueryUpgradeHistoryResponse` | 200 | JWT |
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

`currentStage`: `PENDING` / `TRANSMITTING` / `VERIFYING` / `READY` / `UPGRADING` / `COMPLETED` / `ROLLING_BACK` / `ROLLED_BACK`

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
  "newStatus": "ROLLING_BACK"
}
```

`newStatus` 取值：`ROLLING_BACK`（回滚指令已下发，设备端正在执行回滚） / `ROLLED_BACK`（回滚已完成）。前端应在收到 `ROLLING_BACK` 后持续轮询 `queryUpgradeProgress` 直至 `currentStage` 变为 `ROLLED_BACK`。

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


#### CancelUpgradeTaskResponse

```json
{
  "taskId": "task-uuid-001",
  "previousStatus": "PENDING",
  "cancelledAt": "2026-06-29T08:35:00Z"
}
```

`previousStatus`: 取消前的任务状态。仅 `PENDING` 或 `TRANSMITTING` 阶段的任务允许取消，已进入 `VERIFYING`、`UPGRADING` 及终态（`COMPLETED` / `ROLLED_BACK`）的任务拒绝取消。

> **应用层方法映射（v5 更新）**：以下新增端点对应应用层 `IOTAManagementService` 中已定义的方法（见 `docs/ood_application.md` §3.6 接口方法契约表及 §4.6 DTO 定义）：
> > - `DELETE /api/v1/ota/upgrade-tasks/{taskId}` → `cancelUpgradeTask(taskId: String): CancelUpgradeTaskResponse`
> >   - 入参：`taskId`（路径参数）
> >   - 返回：`CancelUpgradeTaskResponse`（含 `taskId`, `previousStatus`, `cancelledAt`）
> >   - 校验：仅 `PENDING` 或 `TRANSMITTING` 阶段的任务允许取消

**错误响应**：
- `400` — 批量超限（>100）：`BatchSizeExceeded`
- `404` — 车辆/任务不存在（如 `queryUpgradeHistory` 的 `vehicleId` 不存在、`cancelUpgradeTask` 的 `taskId` 不存在）
- `409` — 目标车辆已有进行中升级 / 已处于终态（`COMPLETED` / `ROLLED_BACK`）；任务不可取消（非 PENDING/TRANSMITTING 阶段）：`UpgradeTaskNotCancellable`
- `503` — IoTDA 下指令通道不可达（`IoTDAChannelFailure`，参照 `docs/ood_application.md` §3.6），OTA 指令无法经 IoTDA 下发至设备

---

### 1.7 Auth — 认证服务（跨服务端点）

以下端点为独立于 S1–S6 各应用服务的认证基础设施端点，由云端认证服务（IAM）统一提供，供家属 APP 及前端客户端在进行高敏操作前完成二次身份验证。

| 端点 | 方法 | 路径 | 请求体 | 查询参数 | 响应体 | HTTP 状态码 | 认证 |
|------|------|------|--------|----------|--------|:--:|:--:|
| **用户登录（JWT签发）** | POST | `/api/v1/auth/login` | `LoginRequest` | — | `LoginResponse` | 200 | — |
| **Token 刷新** | POST | `/api/v1/auth/refresh` | `RefreshTokenRequest` | — | `RefreshTokenResponse` | 200 | — |
| **二次身份验证** | POST | `/api/v1/auth/secondary-verify` | `SecondaryVerifyRequest` | — | `SecondaryVerifyResponse` | 200 | JWT |

#### LoginRequest

```json
{
  "authMethod": "PASSWORD",
  "credential": "account@example.com",
  "secret": "********"
}
```

- `authMethod`：`PASSWORD`（用户名+密码）| `SMS_CODE`（手机号+短信验证码）
- `PASSWORD` 模式：`credential` 为用户名或邮箱，`secret` 为密码
- `SMS_CODE` 模式：`credential` 为手机号，`secret` 为短信验证码。`credential` 与 `secret` 两字段的语义由 `authMethod` 决定，两种模式共用同组字段，无需额外引入 `phone`/`smsCode`

#### LoginResponse

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "accountId": "account-042",
  "role": "FAMILY"
}
```

- `accessToken`：JWT access token（有效期默认 1 小时，见 §5.1），用于后续 API 调用的 `Authorization: Bearer <token>` 头
- `refreshToken`：JWT refresh token，用于 access token 过期前换取新 token（§5.1 步骤 5）
- `role`：`FAMILY` / `MANAGER` / `RESCUE`（与 §5.1 JWT `role` 声明一致）

#### RefreshTokenRequest

```json
{
  "refreshToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

- `refreshToken`：登录时获得的 refresh token

#### RefreshTokenResponse

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

- `accessToken`：新签发的 JWT access token
- `refreshToken`：新签发的 refresh token（旧 refresh token 同时失效，采用轮换策略）
- `expiresIn`：新 access token 的有效期（秒），默认 3600

> **认证链路说明**：本端点是 JWT 认证链路的起点——客户端调用 `/api/v1/auth/login` 获取 `accessToken` 后，以 `Authorization: Bearer <accessToken>` 访问 §1.1–§1.6 的所有受保护端点。JWT 的签发（§5.1 步骤 1–2）、校验（步骤 3）、刷新（步骤 5）均依赖此端点产出的 Token。`POST /api/v1/auth/refresh` 端点提供 Token 刷新能力——客户端在 access token 过期前 5 分钟内携带 refresh token 调用此端点换取新的 access token 和 refresh token（轮换策略），避免因 access token 过期导致用户被迫重新登录。401 统一由 API 网关处理（见 §一 开篇约定），本端点自身无需认证头（`认证` 列标注 `—`）。

**错误响应**（login / refresh）：
- `400` — 请求参数无效或 `authMethod` 不支持（login）；`refreshToken` 字段缺失或格式错误（refresh）
- `401` — 认证失败（密码错误、短信验证码错误）（login）；refresh token 过期或无效（refresh）
- `423` — 账户已锁定（连续 5 次密码错误后锁定 15 分钟）（login）
- `429` — 登录尝试次数超限（login）

#### SecondaryVerifyRequest

```json
{
  "accountId": "account-042",
  "method": "OTP",
  "otp": "123456"
}
```

`method`: `OTP` | `BIOMETRIC`（生物特征如指纹/人脸验证完成后由端侧安全模块生成的一次性验证凭证，替代 OTP 字段）。

#### SecondaryVerifyResponse

```json
{
  "secondaryAuthToken": "eyJ...2fa",
  "expiresAt": "2026-06-29T08:15:00Z"
}
```

- `secondaryAuthToken`：二次身份验证凭证，有效期 5 分钟，过期后需重新验证。
- 此 token 需在后续高敏操作（见 §5.2）的请求体中作为 `secondaryAuthToken` 字段携带，由对应应用服务入口校验其有效性和时效性。

**错误响应**：
- `400` — 请求参数无效或验证方法不支持
- `401` — 验证失败（OTP 错误、生物特征不匹配、token 过期）
- `429` — 验证尝试次数超限（连续 5 次失败后锁定 15 分钟）

---

## 二、MQTT 主题设计

边缘侧终端通过华为云 IoTDA（MQTT）与云端通信。以下主题路由表覆盖全部设备-云通信场景，按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）。

### 2.1 主题路由总表

| 方向 | Topic 模板 | QoS | 数据分类 | Payload 说明 |
|:--:|------|:--:|------|------|
| 上报 | `{deviceId}/sensor/{sensorType}/up` | 1 | 流式感知数据 | 单帧 SensorReading（DMS/生理/语音/雷达），频率按通道（DMS ≥10Hz，生理 ≥1Hz，语音 ≥1Hz，雷达按需），topic 按 sensorType 分流 |
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
| 下指令 | `{deviceId}/cmd/media/join/down` | 1 | SparkRTC 入房凭证 | 云端向车机端下发 SparkRTC 房间 ID 和入房 Token，车机端凭此加入 SparkRTC 音视频房间 |
| 响应 | `{deviceId}/cmd/{commandId}/ack` | 1 | 指令执行确认 | 对下指令的 Ack（指令 ID、执行结果、失败原因） |
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
    "gpsLocation": {
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
    "gpsLocation": {
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
    "odometer": { "type": "number" },
    "windowStatuses": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "windowPosition": { "type": "string", "enum": ["FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"] },
          "state": { "type": "string", "enum": ["OPEN", "CLOSED", "PARTIAL", "UNKNOWN", "MOVING"] },
          "lastOperation": { "type": ["string", "null"], "enum": ["OPEN", "CLOSE", "PARTIAL_OPEN", null] },
          "lastOperationResult": { "type": ["string", "null"], "enum": ["SUCCESS", "TIMEOUT", "FAILED", "PENDING", null] },
          "updatedAt": { "type": "string", "format": "date-time" }
        }
      }
    }
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
    "gpsLocation": {
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
| `gpsLocation` | object | | `{ latitude, longitude }` |

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

**SparkRTC 入房凭证下发（cmd/media/join/down）**

| 字段 | 类型 | 必需 | 说明 |
|------|------|:--:|------|
| `commandId` | string | ✓ | 指令 ID |
| `sparkRTCRoomId` | string | ✓ | SparkRTC 房间 ID |
| `sparkRTCJoinToken` | string | ✓ | 车机端加入 SparkRTC 房间的 Token |
| `issuedAt` | string (date-time) | ✓ | 签发时间 |

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
| **救援触发确认** | `rescue_triggered` | `{ "rescueRequestId": "...", "rescueReportId": "...", "status": "PENDING \\| CONFIRMED \\| REJECTED" }` | 手动救援触发确认（包含 S3 请求 ID 和 S5 救援报告 ID）。`status` 枚举与 §1.3 `TriggerManualRescueResponse.status` 语义一致 |
| **Token 续签推送** | `token_renewed` | `{ "sparkRTCRoomId": "...", "sparkRTCJoinToken": "...", "expiresAt": "..." }` | 高危失能场景下 SparkRTC Token 自动续签，家属 APP 收到后更新本地 SparkRTC 客户端 Token |
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

#### 高危失能场景会话时长豁免机制

当系统判定驾驶员进入高危失能状态（如 BR-06 碰撞失能）时，自动激活家属音视频接入，并豁免 10 分钟最大会话时长限制：

| 项目 | 说明 |
|------|------|
| **豁免触发条件** | S1 RiskMonitoringService 边缘侧判定产生 `COLLISION_DISABILITY` 类型告警 → 触发 `emergency_activation` 事件；或 LifeDetectionService 熄火落锁后 60 秒判定窗口内产出 `LifeDetectedEvent` → 触发 `emergency_activation` 事件 |
| **触发组件** | S1 RiskMonitoringService（边缘侧判定）→ 经 MQTT 上报 → S3 RemoteGuardianshipService（云端执行豁免） |
| **豁免范围** | 仅限因紧急激活（`EMERGENCY_ACTIVATION`）而建立的 SparkRTC 会话；家属手动发起的常规对讲仍受 10 分钟限制 |
| **Token 续期策略** | S3 在会话达到 9 分钟时（提前 1 分钟）自动签发新 Token（含更新后的 `expiresAt`），通过 WebSocket `token_renewed` 下行消息推送至家属 APP 和车机端（MQTT `cmd/media/join/down`）。新 Token 的有效期为续签时刻起 10 分钟，支持多次续签直至会话终止 |
| **会话终止条件** | (1) 家属 APP 主动挂断；(2) 驾驶员 HMI 物理遮挡权断开；(3) S1 判定风险解除（riskLevel 降至 L1 以下且持续 ≥30s）→ S3 下发 `access_revoked` |
| **Token 过期与音视频连续性** | SparkRTC SDK 在 Token 过期后不会立即中断已建立的音视频流（其内部仅在重连/重新协商时校验 Token），但为实现平滑过渡，推荐使用上述提前续期策略确保 Token 始终有效 |

---

## 四、ArkTS 前端对接契约

### 4.1 家属 APP（HarmonyOS）对接接口清单

家属 APP 基于 ArkTS 开发，运行于 HarmonyOS 设备。下为其调用的全部后端接口清单与数据模型。

> **对讲建立通道说明**：家属 APP 建立音视频对讲支持双通道——(1) **WebSocket 优先**：通过 `request_media` 上行消息发起对讲请求（见 §3.1），云端处理后经 WebSocket 回复 `access_granted` 下行消息（含 SparkRTC 入房凭证）；(2) **REST 备用**：当 WebSocket 未连接或因网络原因不可用时，可降级通过 REST `POST /api/v1/guardianship/media-session` 发起请求并同步获取 `RequestMediaSessionResponse`（含 `sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken` 四个字段，完整定义见 §1.3）。两种通道返回的数据模型结构和语义完全一致，ArkTS 前端应优先使用 WebSocket 通道，REST 作为降级方案。

#### REST API 调用列表

| 功能 | 方法 | 路径 | 请求体/模型 | 响应体/模型 |
|------|------|------|---------|---------|
| 请求建立音视频对讲 | POST | `/api/v1/guardianship/media-session` | `RequestMediaSessionReq` | `RequestMediaSessionResp` |
| 查询驾驶员风险状态 | GET | `/api/v1/drivers/{driverId}/risk-status` | — | `GetDriverRiskStatusResponse` |
| 更新通知偏好 | PUT | `/api/v1/guardianship/notification-preference` | `UpdateNotificationPreferenceReq` | — |
| 一键救援触发 | POST | `/api/v1/guardianship/manual-rescue` | `TriggerManualRescueReq` | `TriggerManualRescueResp` |
| 远程车窗控制 | POST | `/api/v1/guardianship/window-control` | `ControlVehicleWindowReq` | — |
| 查询车窗状态 | GET | `/api/v1/vehicles/{vehicleId}/windows` | — | `QueryWindowStatusResp` |
| 终止音视频会话 | DELETE | `/api/v1/guardianship/media-session/{sessionHandle}` | — | — |
| 查询家属监护权限 | GET | `/api/v1/guardianship/{driverId}/permissions` | — | `QueryGuardianshipPermissionsResp` |
| 签发 SparkRTC Token | POST | `/api/v1/sparkrtc/token` | `IssueSparkRTCTokenReq` | `IssueSparkRTCTokenResp` |

> **命名约定**：§4.1 ArkTS DTO 类型名使用缩写后缀 `Req`（= Request）和 `Resp`（= Response），与 §1 REST API 契约中的全名 `Request` / `Response` 等价对应。此约定兼顾 ArkTS/前端生态的简洁命名惯例与后端契约的清晰性，跨团队协作时以全名为准，缩写为便捷别名。

#### ArkTS 数据模型定义（DTO）

```typescript
// === 类型别名 ===
type AlertType = 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY' | 'PERFORMANCE_WARNING'
type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'
type StatusColor = 'GREEN' | 'YELLOW' | 'RED'
type MediaSessionType = 'AUDIO' | 'VIDEO'
type RescueRequestStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED'
type WindowControlOperation = 'OPEN' | 'CLOSE' | 'PARTIAL_OPEN'
type WindowPosition = 'FRONT_LEFT' | 'FRONT_RIGHT' | 'REAR_LEFT' | 'REAR_RIGHT'
type WindowState = 'OPEN' | 'CLOSED' | 'PARTIAL' | 'UNKNOWN' | 'MOVING'
type WindowOperationResult = 'SUCCESS' | 'TIMEOUT' | 'FAILED' | 'PENDING'
type GuardianshipPermissionType = 'MEDIA_CALL' | 'WINDOW_CONTROL' | 'MANUAL_RESCUE' | 'STATUS_MONITORING'
type CareRelationshipStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED'
type SparkRTCRole = 'subscriber' | 'publisher'
type TripStatus = 'NOT_STARTED' | 'ACTIVE' | 'COMPLETED'
type AccessGrantReason = 'REGULAR_60S' | 'EMERGENCY_ACTIVATION' | 'OCCLUSION_RECOVERY'
type AccessRevokeReason = 'RISK_DECLINED' | 'CAMERA_OCCLUDED' | 'DRIVER_DEACTIVATED'

// === GetDriverRiskStatusResponse ===
interface GetDriverRiskStatusResponse {
  hasActiveTrip: boolean
  activeAlerts: Array<ActiveAlertEntry>
  derivedStatusColor: StatusColor
}

interface ActiveAlertEntry {
  alertType: AlertType
  riskLevel: RiskLevel
}

// === RequestMediaSessionReq / Resp ===
interface RequestMediaSessionReq {
  familyAccountId: string
  driverId: string
  sessionType: MediaSessionType
  secondaryAuthToken: string
}

interface RequestMediaSessionResp {
  sessionHandle: string
  sessionToken: string
  sparkRTCRoomId: string
  sparkRTCJoinToken: string
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
  rescueReportId: string                  // S5 救援报告 ID，用于后续 queryRescueHistory 查询救援进展
  status: RescueRequestStatus
}

// === ControlVehicleWindowReq ===
interface ControlVehicleWindowReq {
  familyAccountId: string
  driverId: string
  windowOperation: WindowControlOperation
  windowPosition: WindowPosition
  secondaryAuthToken: string
}

// === QueryWindowStatusResp ===
interface QueryWindowStatusResp {
  windowStatuses: Array<WindowStatusEntry>
}

interface WindowStatusEntry {
  windowPosition: WindowPosition
  state: WindowState
  lastOperation?: WindowControlOperation
  lastOperationResult?: WindowOperationResult
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
  permissionType: GuardianshipPermissionType
  granted: boolean
  grantedAt: string
  expiresAt?: string
}

interface CareRelationshipSummary {
  status: CareRelationshipStatus
  establishedAt: string
}

// === IssueSparkRTCTokenReq / Resp ===
interface IssueSparkRTCTokenReq {
  roomId: string
  userId: string
  role: SparkRTCRole
}

interface IssueSparkRTCTokenResp {
  token: string
  expiresAt: string                         // ISO 8601
}

// === WebSocket 下行消息模型 ===
interface DriverStatusSnapshot {
  driverId: string
  vehicleId: string
  timestamp: string
  activeAlertLevels: Record<AlertType, RiskLevel>
  gpsLocation?: GeoPoint
  speed?: number
  tripStatus: TripStatus
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
  reason: AccessGrantReason
}

interface AccessRevokedMessage {
  driverId: string
  reason: AccessRevokeReason
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
| L3 高危告警实时推送 | WebSocket 订阅 | `wss://api.example.com/ws/fleet?token=<JWT>` | 事件驱动（AlertTriggeredEvent L3） |
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

### 5.1 API 认证——JWT

> **设计决策：不采用 OAuth2 协议**。本系统不采用 OAuth2 作为 API 认证协议，基于以下设计理由：
> > 1. **客户端类型单一**：本系统的 API 消费方仅包含家属 APP（HarmonyOS ArkTS）、车队管理大屏（Web）和救援机构控制台三类第一方客户端，无需支持第三方应用授权（OAuth2 的核心场景为委托授权——允许第三方应用以用户名义访问资源）；
> > 2. **无资源持有者密码凭证委托场景**：系统不涉及"用户授权第三方服务代表自己访问其数据"的用例（如允许某数据分析平台代用户拉取其驾驶行为报告）；所有 API 访问均由前端客户端代表已认证用户直接发起；
> > 3. **JWT Bearer Token 已满足需求**：系统的认证需求（用户身份验证、角色权限控制、Token 过期与刷新）均可通过 JWT + refresh token 方案覆盖，无需引入 OAuth2 的授权码流程、客户端注册和 scope 委托管理带来的额外复杂度；
> > 4. **敏感操作二次验证采用独立机制**：家属远程对讲/车窗控制等高敏操作的二次身份验证（见 §5.2）通过独立 IAM 认证服务的 OTP/生物特征验证实现，不依赖 OAuth2 的授权码流程；
> > 5. **未来扩展性预留**：若未来引入第三方生态（如保险公司查询驾驶行为评分），可在 API 网关层叠加 OAuth2 授权服务器，当前 JWT 架构作为资源服务器认证方式可平滑衔接到 OAuth2 体系，无需推翻重来。
>
> 综上，当前设计以 JWT Bearer Token 为 API 认证方案，放弃 OAuth2 是基于系统实际需求的主动选择，而非遗漏。

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

| 限流层级 | 限制规格 | 桶容量 | 适用端点 |
|:--:|------|:--:|------|
| **全局** | 1000 req/s / 单个 API 网关实例 | 1500 | 全部 REST 端点 |
| **家属角色** | 30 req/s / 账户 | 45 | S3 全部端点 + WebSocket 消息 |
| **管理员角色** | 60 req/s / 账户 | 90 | S4 看板/报告/钻取 |
| **救援机构角色** | 20 req/s / 账户 | 30 | S5 全部端点 |
| **高敏操作** | 3 req/min / 账户 | 5 | 对讲建立、车窗控制、远程解锁、手动救援 |
| **报告生成** | 5 req/min / 管理员 | 8 | S4 `generateReport`（消耗数据库与 CPU） |
| **MQTT 上报** | IoTDA 设备级限速：100 msg/s / 设备 | 150 | 设备→云端感知数据上报（超过则 IoTDA 侧拒绝） |

> 令牌桶参数均可动态配置（Nacos/配置中心）。突发容忍度为桶容量的 1.5 倍——例如全局速率 1000 req/s 时实际允许的瞬时突发为 1500 req/s（由 1500 的桶容量吸收）。超限时返回 HTTP `429 Too Many Requests`，响应头 `Retry-After` 指示等待秒数。家属 APP 收到 429 后执行退避重试。

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
| S3 写操作请求体 `familyAccountId` 校验 | S3 所有写操作入口（`requestMediaSession`、`updateNotificationPreference`、`triggerManualRescue`、`controlVehicleWindow`） | 校验请求体中的 `familyAccountId` 与 JWT `sub`（账户 ID）一致；不一致则拒绝请求（HTTP `403`）并记录安全审计日志。防止横向越权——恶意客户端传入其他账户 ID 代为操作 |

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

## 修订说明（v3）

以下变更基于审查文件（a_v3_iteration_requirement.md）的 8 个 LOCATED 问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重）：新增 API 端点（GET permissions、POST sparkrtc/token、DELETE upgrade-tasks/{taskId}）未映射到应用层方法 | **修改** — 在 §1.3 S3 新增"应用层方法预签名"标注块，明确 `queryGuardianshipPermissions(driverId, accountId): QueryGuardianshipPermissionsResponse` 和 `issueSparkRTCToken(request): IssueSparkRTCTokenResponse` 的预签名；在 §1.6 S6 新增同样标注块，明确 `cancelUpgradeTask(taskId): CancelUpgradeTaskResponse` 的预签名。均注明完整方法签名见 `docs/ood_application.md`。 |
| 问题 2（中等）：家属手动救援触发端点 `POST /api/v1/guardianship/manual-rescue` 归属 S3，与需求 S5 分组不一致 | **保留（添加说明）** — 在 §1.3 开篇添加设计说明块，解释归口调整的三个理由：(1) 路径前缀 `guardianship` 表示家属监护行为，与 S3 职责内聚；(2) 调用方为家属 APP 非救援机构；(3) S5 面向 RESCUE 角色，手动救援不要求该角色。方案 A（推荐：添加说明）而非方案 B（移至 S5）。 |
| 问题 3（中等）：S5 queryRescueHistory 认证标注 `JWT` 与 §5.1 角色映射 `RESCUE` 矛盾 | **修改** — 将 §1.5 `queryRescueHistory` 端点的认证列从 `JWT` 统一为 `JWT (RESCUE)`，与 §5.1 角色→权限映射表一致。 |
| 问题 4（一般）：SparkRTC Token 端点 `POST /api/v1/sparkrtc/token` 的消费者未在 §4.1 中列出 | **修改** — 在 §4.1 REST API 调用列表中补入 `签发 SparkRTC Token` 行；同步补充 `IssueSparkRTCTokenReq` / `IssueSparkRTCTokenResp` 的 ArkTS 接口定义。 |
| 问题 5（一般）：TriggerRollbackResponse JSON 示例仅展示 `ROLLED_BACK`，缺失 `ROLLING_BACK` 中间状态 | **修改** — 将示例 `newStatus` 改为 `ROLLING_BACK`；补充说明两种取值含义及前端轮询建议（收到 `ROLLING_BACK` 后轮询 `queryUpgradeProgress` 直至终态）。 |
| 问题 6（一般）：S4 report download 端点未指定 Content-Type | **修改** — 在 §1.4 报告文件下载端点下方新增下载响应头说明块，明确 `format=pdf → application/pdf`、`format=xlsx → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，及 `Content-Disposition` 头。 |
| 问题 7（轻微）：MQTT 主题模板 `${sensorType}` 使用 `$` 前缀，与其余 `{variable}` 语法不一致 | **修改** — 将 §2.1 主题路由总表中 `${sensorType}` 统一为 `{sensorType}`。 |
| 问题 8（轻微）：DELETE 响应码约定不统一（S3 返回 204，S6 返回 200 带响应体） | **修改** — 在文档开篇（§一 总述段落后）新增 DELETE 响应码约定说明块，阐明混合策略的适用场景及与 REST 语义的兼容性。 |

## 修订说明（v4）

以下变更基于审查文件（a_v4_iteration_requirement.md）的 8 个 LOCATED 问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重）：S3→S5 救援记录的 ID 体系断裂（`rescueRequestId` vs `rescueReportId`） | **修改** — 在 TriggerManualRescueResponse 中新增 `rescueReportId` 字段，补充字段说明；新增"S3→S5 手动救援流转说明"块，阐明 S3 内部编排 S5 createRescueReport() 并关联两 ID 的流转逻辑；同步更新 §3.1 WebSocket 下行 `rescue_triggered` 消息携带 `rescueReportId`。 |
| 问题 2（严重）：新增 API 端点的应用层方法预签名仍为承诺性标注，应用层 OOD 未落地 | **修改** — 将 §1.3 和 §1.6 的"预签名"标注升级为 v4 版，增加更完整的方法签名描述（含入参来源说明、返回模型说明、校验约束），并明确标注这些方法需在 `docs/ood_application.md` 对应接口中落地。 |
| 问题 3（中等）：S3 请求体中的 `familyAccountId` 可被客户端篡改 | **修改** — 在 §1.3 新增"安全门控"说明块，明确应用层入口须校验 `familyAccountId` 与 JWT `sub` 一致，不一致则拒绝并记录审计日志；在 §5.6 隐私边界校验表中新增一行 `S3 写操作请求体 familyAccountId 校验`。 |
| 问题 4（中等）：MQTT Topic `cmd/media/join/down` 定义缺失 | **修改** — 在 §2.1 主题路由总表中新增下行 `{deviceId}/cmd/media/join/down` 行；在 §2.2 中新增其 Payload 字段定义表（含 `commandId`、`sparkRTCRoomId`、`sparkRTCJoinToken`、`issuedAt`）。 |
| 问题 5（一般）：S4 QueryTrajectoryResponse 的 `dataConsistency` 字段未在响应模型中定义 | **修改** — 在 QueryTrajectoryResponse JSON 示例中新增 `dataConsistency` 字段（类型 `string`，枚举 `CONSISTENT \| INCONSISTENT`），并补充枚举说明和触发条件。 |
| 问题 6（一般）：§4.1 家属 APP REST API 调用列表遗漏 `requestMediaSession` 端点 | **修改** — 在 §4.1 REST API 调用列表中新增"请求建立音视频对讲"行；补充 `RequestMediaSessionReq` / `RequestMediaSessionResp` 的 ArkTS 接口定义；在 §4.1 开篇新增"对讲建立通道说明"块，明确 WebSocket 优先、REST 降级的双通道策略。 |
| 问题 7（轻微）：S6 cancelUpgradeTask 端点行表格列数异常（8 列变 7 列） | **修改** — 删除 `cancelUpgradeTask` 行中多余的 `—` 单元格，使列数恢复为 7 列，与表头对齐。 |
| 问题 8（轻微）：ArkTS DTO 类型名使用缩写（Req/Resp）与 REST API 全称（Request/Response）不一致 | **修改** — 在 §4.1 REST API 调用列表与 ArkTS DTO 定义之间新增命名约定说明块，声明 Req=Request、Resp=Response 的等价对应关系及跨团队协作以全名为准的原则。 |

## 修订说明（v5）

以下变更基于审查文件（a_v4_review_v1.md）的 3 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重）：ArkTS `TriggerManualRescueResp` 接口缺失 `rescueReportId` 字段，导致 S3→S5 ID 关联链路在前端侧断裂 | **修改** — 在 §4.1 ArkTS `TriggerManualRescueResp` interface 中新增 `rescueReportId: string` 字段，与 §1.3 TriggerManualRescueResponse 保持一致。同时同步更新 `docs/ood_application.md` §4.3 的 `TriggerManualRescueResponse` DTO，补充 `rescueReportId` 字段及说明。 |
| 问题 2（严重）：新增 API 端点的应用层方法仍未在 `docs/ood_application.md` 中实际定义（持续 3 轮未根本解决） | **修改** — 在 `docs/ood_application.md` 中：(1) §3.3 `IRemoteGuardianshipService` 接口方法表新增 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法行；(2) §3.6 `IOTAManagementService` 接口方法表新增 `cancelUpgradeTask` 方法行及对应异常处理条目；(3) §4.3 S3 DTO 新增 `QueryGuardianshipPermissionsRequest/Response`、`GuardianshipPermissionEntry`、`CareRelationshipSummary`、`IssueSparkRTCTokenRequest/Response` 及相关枚举类型；(4) §4.6 S6 DTO 新增 `CancelUpgradeTaskRequest/Response`。同时将 API OOD §1.3 和 §1.6 中的"预签名"标注升级为"应用层方法映射（v5 更新）"，指向 `docs/ood_application.md` 中已定义的方法。 |
| 问题 3（一般）：§4.1 "对讲建立通道说明"块对 REST 响应字段的简化描述不完整（仅提及 2 个字段，实际为 4 个） | **修改** — 将说明块中 REST 备用通道的字段列举从 `含 sessionHandle + sparkRTCJoinToken` 更新为完整 4 项（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），并添加对 §1.3 完整模型定义的交叉引用。 |

## 修订说明（v6）

以下变更基于审查文件（a_v5_iteration_requirement.md）的 8 个 LOCATED 问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重·接口契约缺失）：S3→S5 手动救援流转引用了未在接口中形式化定义的 `createRescueReport` 方法 | **修改** — 在 §1.3 S3→S5 手动救援流转说明块后新增"⚠ 接口契约待补"注解块，明确标注 `IEmergencyRescueService.createRescueReport()` 方法引用存在但尚未在应用层 OOD 接口方法表中形式化定义，并说明下游实现者需待该接口落地后方可完整实现编排逻辑。 |
| 问题 2（一般·跨层不一致）：RequestMediaSessionRequest 的 `secondaryAuthToken` 字段在应用层 OOD 中缺失 | **保留（无需修改本文件）** — API OOD §1.3 `RequestMediaSessionRequest` JSON 示例已包含 `secondaryAuthToken` 字段（第 154 行），本文档的接口契约定义是完整的。问题根源在于 `docs/ood_application.md` §4.3 的同名 DTO 缺失该字段，需在应用层 OOD 中补充。 |
| 问题 3（一般·跨层不一致）：TriggerManualRescueRequest 的 `secondaryAuthToken` 字段在应用层 OOD 中缺失 | **保留（无需修改本文件）** — 与问题 2 同理，API OOD §1.3 `TriggerManualRescueRequest` JSON 示例已包含 `secondaryAuthToken` 字段（第 193 行），本文档的接口契约定义完整。需在 `docs/ood_application.md` §4.3 的同名 DTO 中补充。 |
| 问题 4（一般·细节缺失）：高危失能场景下 SparkRTC 会话时长豁免机制未定义 | **修改** — 在 §3.2 新增"高危失能场景会话时长豁免机制"专节，详细定义：(1) 豁免触发条件（S1 判定 COLLISION_DISABILITY 或 LIFE_DETECTION_PROLONGED）；(2) 触发组件及执行路径（S1 边缘判定 → S3 云端执行）；(3) 豁免范围（仅限 EMERGENCY_ACTIVATION 会话）；(4) Token 续期策略（会话 9 分钟时提前续签，通过 WebSocket `token_renewed` 和 MQTT `cmd/media/join/down` 下发）；(5) 会话终止条件；(6) Token 过期与音视频连续性说明。同步在 §3.1 WebSocket 下行消息表新增 `token_renewed` 消息类型。 |
| 问题 5（轻微·安全与内部不一致）：车队大屏 WebSocket 端点使用 `ws://` 而非 `wss://`，且同节内表格与代码示例自相矛盾 | **修改** — 将 §4.2 看板数据订阅模型表中 L3 高危告警实时推送的 WebSocket 端点从 `ws://` 修正为 `wss://`，与同节 TypeScript 代码示例（第 1643 行）及 §5.5 加密策略保持一致。 |
| 问题 6（轻微·语义模糊）：S3 TriggerManualRescueResponse.status 与 S5 RescueRecordSummary.status 使用不同枚举值表示同一语义状态 | **修改** — 在 §1.3 S3→S5 手动救援流转说明块后新增"状态枚举映射"块，明确定义映射关系：S3 `PENDING` ↔ S5 `SENT`、S3 `CONFIRMED` ↔ S5 `CONFIRMED`、S3 `REJECTED` ↔ S5 `MANUAL_ESCALATION`，并说明两处枚举值的语义角色差异（S3 为面向用户即时状态，S5 为完整生命周期状态）。 |
| 问题 7（轻微·格式不一致）：S2 端点表缺失"查询参数"列 | **修改** — 为 §1.2 S2 端点表补充"查询参数"列（表头从 7 列扩展为 8 列），与 S1 端点表格式统一。将原先以自然语言描述的 `queryInterventionHistory` 查询参数 `page`、`size`、`startTime`、`endTime` 结构化填入表格。 |
| 问题 8（轻微·逻辑张力）：SparkRTC Token 端点的 `role` 参数包含 `publisher` 值，但端点归属 FAMILY 角色 | **修改** — 在 §1.3 `IssueSparkRTCTokenRequest` 的 `role` 字段说明后新增"安全约束"注解块，明确规定后端须校验调用方 JWT `AccountRole` 与请求 `role` 字段一致性，FAMILY 角色仅可请求 `role=subscriber`；`publisher` Token 仅通过 MQTT `cmd/media/join/down` 下发至车机端。 |

## 修订说明（v7）

以下变更基于审查文件（a_v6_iteration_requirement.md，审查结论选自 b_v5_diag_v2.md）的 11 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重·接口契约缺失）：`createRescueReport` 方法未形式化定义 | **保留** — 问题根源在 `docs/ood_application.md` §3.5 接口方法表缺失该方法的完整契约定义。API OOD 中"⚠ 接口契约待补"注解已于 v6 添加，待应用层 OOD 补全方法定义后移除。本条为持续性问题，多轮迭代未根本解决。 |
| 问题 2（中等·跨层 DTO 不一致）：`secondaryAuthToken` 在 `RequestMediaSessionRequest` 缺失 | **保留（无需修改本文件）** — API OOD §1.3 `RequestMediaSessionRequest` JSON 示例已包含 `secondaryAuthToken` 字段。问题根源在 `docs/ood_application.md` §4.3 同名 DTO 缺失该字段，需在应用层 OOD 中补充。 |
| 问题 3（中等·跨层 DTO 不一致）：`AlertSummary` 缺失 `gpsLocation` 字段 | **保留（无需修改本文件）** — API OOD §1.1 `QueryAlertHistoryResponse` JSON 示例已包含 `gpsLocation` 字段（第 51 行），且 §4.1 ArkTS `AlertSummary` 接口已包含 `gpsLocation?: GeoPoint`。问题根源在 `docs/ood_application.md` §4.1 同名 DTO，需在应用层 OOD 中补充。 |
| 问题 4（中等·ArkTS 类型不完整）：`AlertType` 和 `RiskLevel` 枚举取值不全且无独立类型定义 | **修改** — 在 §4.1 ArkTS 数据模型定义顶部新增独立的 `type AlertType` 和 `type RiskLevel` 类型别名，包含完整枚举值。移除 `ActiveAlertEntry` 接口中冗余的行内注释，统一引用独立类型定义。 |
| 问题 5（中等·端点表缺少查询参数列）：S5 和 S6 端点表缺失"查询参数"列 | **修改** — 将 S5 和 S6 端点表从 7 列扩展为 8 列（补充"查询参数"列）。将 S5 `queryRescueHistory` 的查询参数 `driverId`、`vehicleId`、`startTime`、`endTime`、`page`、`size` 及 S6 `queryUpgradeProgress` 的 `vehicleIds`、`queryUpgradeHistory` 的 `page`/`size` 纳入表格对应行。移除表外冗余的查询参数散文描述。 |
| 问题 6（中等·S2 缺失错误响应文档）：S2 端点表无错误响应小节 | **修改** — 在 §1.2 末尾补充错误响应小节，覆盖 400（参数无效）、404（行程不存在时返回空集合，不视为错误）、503（服务不可用）。 |
| 问题 7（一般·跨层 DTO 不一致）：`QueryTrajectoryResponse` 缺失 `dataConsistency` 字段 | **保留（无需修改本文件）** — API OOD §1.4 `QueryTrajectoryResponse` JSON 示例已包含 `dataConsistency` 字段（第 385 行，枚举值 `CONSISTENT \| INCONSISTENT`）。问题根源在 `docs/ood_application.md` §4.4 同名 DTO 缺失该字段及 `DataConsistency` 枚举，需在应用层 OOD 中补充。 |
| 问题 8（轻微·前端对接清单遗漏）：§4.1 REST API 调用列表遗漏 `endMediaSession` 端点 | **修改** — 在 §4.1 REST API 调用列表中补充"终止音视频会话"行（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`），响应码 204 无响应体。 |
| 问题 9（轻微·S5 内部接口可见性）：S5 内部编排方法文档可读性不足 | **修改** — 在 §1.5 S5 错误响应后添加"内部编排接口说明"块，明确 S5 的 `createRescueReport` 供 S3 内部编排调用、不对外暴露为独立 REST 端点，并交叉引用 §1.3 S3→S5 流转说明。 |
| 问题 10（轻微·MQTT 语法不一致）：`${commandId}` 与其余 `{variable}` 语法不一致 | **修改** — 将 §2.1 主题路由表中 `${commandId}` 统一为 `{commandId}`。 |
| 问题 11（轻微·S4 错误响应不完整）：S4 端点表无错误响应小节 | **修改** — 在 §1.4 末尾补充错误响应小节，覆盖 400（参数无效）、401（未经认证）、404（资源不存在）、504（报告生成超时，已有）。 |

## 修订说明（v8）

以下变更基于审查文件（a_v7_iteration_requirement.md，审查结论选自 b_v6_diag_v1.md）的 5 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（轻微·S3 端点表缺失查询参数列）：S3 端点表表头 7 列，与 S1/S2/S4/S5/S6 的 8 列格式不统一 | **修改** — 将 §1.3 S3 端点表表头从 7 列扩展为 8 列（补充"查询参数"列），各端点查询参数列填 `—`。 |
| 问题 2（轻微·S4 端点表 blockquote 导致表格断裂）：报告文件下载行与绩效预警订阅行之间插入了 `> ` blockquote 注释块，导致 Markdown 表格断裂为两张独立表 | **修改** — 将下载响应头说明块移至 S4 端点表之后（`SubscribePerformanceWarningResponse` 行之后），恢复 S4 端点表为单张连续表格。 |
| 问题 3（一般·StatusColor 枚举跨层不一致）：应用层 OOD 引入未定义的 `ORANGE` 值，领域层 VO-15 仅定义三值（`GREEN \| YELLOW \| RED`） | **修改** — 在 §1.1 `derivedStatusColor` 字段说明后补充一致性注解：明确三值枚举与领域层 VO-15 一致；若应用层引用额外值，应以领域层定义为准统一。问题根源在 `docs/ood_application.md`，需在应用层 OOD 中移除 `ORANGE` 或统一为三值。 |
| 问题 4（一般·LIFE_DETECTION_PROLONGED 缺乏跨层定义）：API OOD 直接引用该概念但领域层和应用层均无形式化定义 | **修改** — 将 §3.2 豁免触发条件中的 `LIFE_DETECTION_PROLONGED` 替换为对既有概念的描述性引用：`LIFE_DETECTION` 类型告警持续异常 ≥60s。此方式避免引入未定义的形式化概念，下游可直接依据现有 `LIFE_DETECTION` 告警类型的持续判定逻辑实现。 |
| 问题 5（轻微·§5.1 标题 OAuth2 与正文不匹配）：标题含 OAuth2，正文仅描述 JWT | **修改** — 将 §5.1 标题从"API 认证——JWT / OAuth2"改为"API 认证——JWT"。本设计不采用 OAuth2 协议，标题应与正文内容一致。 |

## 修订说明（v9）

以下变更基于审查文件（a_v8_iteration_requirement.md，审查结论选自 b_v7_diag_v1.md）的 5 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重·跨层 DTO 不一致）：`RequestMediaSessionResponse` 跨层 DTO 不一致，应用层 OOD 同名 DTO 缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken` | **保留（无需修改本文件）** — API OOD §1.3 `RequestMediaSessionResponse` JSON 示例（4 字段）及 §4.1 ArkTS `RequestMediaSessionResp` 接口均已包含完整的 `sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken` 四个字段，本文档的接口契约定义是完整的。问题根源在 `docs/ood_application.md` §4.3 的同名 DTO 仅定义 2 个字段，需在应用层 OOD 中补充后两者。 |
| 问题 2（一般·需求响应缺陷）：§5.1 标题经 v8 修订仅保留 JWT，但正文未提供排除 OAuth2 的设计理由 | **修改** — 在 §5.1 开篇新增"设计决策：不采用 OAuth2 协议"说明块，从客户端类型单一、无委托授权场景、JWT 已满足需求、二次验证机制独立、未来扩展性预留五个维度阐述排除理由。 |
| 问题 3（一般·接口契约不完整）：§5.2 引用的 `POST /api/v1/auth/secondary-verify` 端点未在 §1 REST API 契约中定义 | **修改** — 在 §1 新增 §1.7 Auth 认证服务独立小节，正式定义 `POST /api/v1/auth/secondary-verify` 端点（含 `SecondaryVerifyRequest` / `SecondaryVerifyResponse` 模型、`method` 枚举取值 OTP/BIOMETRIC、5 分钟有效期、错误响应）。归属为跨服务认证基础设施端点，独立于 S1–S6 各应用服务。 |
| 问题 4（一般·ArkTS DTO 类型定义不完整）：13 个枚举/字面量类型（`StatusColor`、`MediaSessionType`、`RescueRequestStatus`、`WindowControlOperation`、`WindowPosition`、`WindowState`、`WindowOperationResult`、`GuardianshipPermissionType`、`CareRelationshipStatus`、`SparkRTCRole`、`TripStatus`、`AccessGrantReason`、`AccessRevokeReason`）仅以行内注释存在，缺少独立 `type` 声明 | **修改** — 在 §4.1 ArkTS 类型别名区新增上述 13 个独立 `type` 别名声明，覆盖全部枚举值。同步清理所有接口字段上已冗余的行内注释，统一替换为类型引用。 |
| 问题 5（轻微·版本标识不一致）：v7 轮次文件名（`a_v7_copy_from_v6.md`）、文档标题（a_v8 / v8）、迭代轮次（7）三者版本号不一致 | **修改** — 将文档标题从"a_v8 / v8"更新为"a_v9 / v9"，与当前迭代轮次（v9）一致。文件名 `a_v8_copy_from_v7.md` 为系统指定的产出输出路径，不在本文档可控范围，但标题已修正以消除阅读歧义。 |
| 问题 1（严重·Markdown 格式错误）：§1.6 TriggerRollbackResponse JSON 代码块后存在多余的独立 ``` 行（第 646 行） | **修改** — 删除多余的 ``` 行，恢复 Markdown 代码块配对。 |
| 问题 2（中等·版本标识与当前轮次矛盾）：文档标题为"a_v9 / v9"，但文件实际为第 8 轮产出 | **保留（无需修改）** — 经审查，当前产出文件为 `a_v9_copy_from_v8.md`（第 9 轮），标题中的 v9 与文件名前缀和实际轮次三者一致。上一轮所发现的"文件名 v8 / 标题 v9 / 轮次 8"矛盾已在本轮因文件名升级为 a_v9 而自然消解。 |
| 问题 3（中等·SparkRTCRole publisher 为设计死值）：REST 端点 `role` 字段和 ArkTS `SparkRTCRole` 类型包含 `publisher` 取值，但 FAMILY 角色无合法调用路径 | **修改** — 在 §1.3 `IssueSparkRTCTokenRequest` 的 `role` 字段说明中明确 `subscriber` 为 REST 端点唯一合法取值，声明前端传入 `publisher` 时后端拒绝。将安全约束注解升级为"安全约束与天花板限制"，补充`400 Bad Request` (`InvalidRoleForEndpoint`) 的明确拒绝语义。ArkTS `SparkRTCRole` 类型保留 `'publisher'` 取值（该类型覆盖 SparkRTC 完整角色空间，天花板限制体现在 API 契约层而非类型层）。 |
| 问题 4（中等·请求体示例缺失）：§1.4 引用 `SubscribePerformanceWarningRequest` 但缺少对应 JSON 示例 | **修改** — 在 §1.4 新增 `SubscribePerformanceWarningRequest` JSON 示例（含 `adminId`、`fleetId` 字段），并补充字段说明及 `adminId` 的 JWT 一致性校验要求。 |
| 问题 5（一般·WebSocket 消息枚举值缺失）：§3.1 `rescue_triggered` 下行消息的 `status` 字段未枚举可能取值 | **修改** — 将 `status` 字段值从 `"..."` 改为 `"PENDING \| CONFIRMED \| REJECTED"`，并添加注释"与 §1.3 TriggerManualRescueResponse.status 语义一致"。 |
| 问题 6（轻微·S1/S5 错误响应 401 缺失）：S1 和 S5 的错误响应列表中未包含 401，而 S4 等节显式列出 | **修改** — 采用方案 (b)：在 §一 总述段落后新增"401 统一处理约定"声明，明确 JWT 校验导致的 401 由 API 网关统一处理，各端点不单独标注。同步移除 S4 错误响应中已冗余的 401 条目（`- \`401\` — 未经认证（JWT 缺失或无效）`）。保留 §1.3 应用层特定的 401（`SecondaryAuthRequired`，非网关级）。 |
| 版本标识不一致：最新修订块标记为"修订说明（v10）"（第 2040 行），与文档标题 a_v9 / v9 矛盾 | **修改** — 将"修订说明（v10）"修正为"修订说明（v9）"，使修订编号与文档标题版本号一致。 |

## 修订说明（v10）

以下变更基于审查文件（a_v10_iteration_requirement.md）的 5 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重·接口契约缺失）：`createRescueReport` 方法未在 `docs/ood_application.md` 中形式化定义，持续 6 轮未解决 | **修改** — 在 `docs/ood_application.md` §3.5 接口方法表中新增 `createRescueReport` 方法行（含 `CreateRescueReportRequest`/`CreateRescueReportResponse` DTO、事务属性、异常处理）；在 §4.5 补充 `CreateRescueReportRequest`/`CreateRescueReportResponse` DTO 定义。同步更新 API OOD §1.3 "⚠ 接口契约待补"注解为"接口契约已落地（v10）"，更新 §1.5 内部编排接口说明块中的交叉引用。 |
| 问题 2（中等·文档结构错误）：产出末尾存在多个题为"修订说明（v9）"的独立修订块 | **修改** — 将三个 v9 修订块合并为第一个 v9 修订块下的统一表格，删除重复的"修订说明（v9）"标题和独立的修订块结构。 |
| 问题 3（中等·枚举定义不完整）：`TriggerRollbackResponse.newStatus` 含 `ROLLING_BACK` 但 `QueryUpgradeProgressResponse.currentStage` 枚举不包含该值 | **修改** — 在 §1.6 `currentStage` 枚举中补充 `ROLLING_BACK` 值（位于 `COMPLETED` 之后、`ROLLED_BACK` 之前）。同步更新 `docs/ood_domain.md` VO-19 `UpgradeStage` 枚举。 |
| 问题 4（一般·数据链路缺失）：`vehicle/state/up` VehicleStateSnapshot 不包含车窗状态字段，但 `QueryWindowStatusResponse` 和 `DriverStatusSnapshot.windowStatus` 依赖该数据 | **修改** — 在 §2.2 VehicleStateSnapshot JSON Schema 中新增 `windowStatuses` 字段（数组，复用 `WindowStatusEntry` 结构），补齐车窗状态周期性遥测上报数据链路。 |
| 问题 5（轻微·命名不一致）：MQTT SafetyAlertEvent 的 GPS 字段命名为 `gps`，但 REST QueryAlertHistoryResponse 中为 `gpsLocation` | **修改** — 将 §2.2 SafetyAlertEvent JSON Schema 中 `gps` 字段重命名为 `gpsLocation`，与 REST 契约统一；同步更新 §1.1 字段说明文本中的交叉引用。 |

## 修订说明（v11）

以下变更基于审查文件（a_v11_iteration_requirement.md）的 8 个问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（严重·认证链路不完整）：缺少 JWT 登录/Token 签发端点，认证链路不完整 | **修改** — 在 §1.7 新增 `POST /api/v1/auth/login` 端点及 `LoginRequest`/`LoginResponse` DTO。支持 `PASSWORD`（用户名+密码）和 `SMS_CODE`（短信验证码）两种认证方式。登录端点自身无需 JWT 认证头（认证链路的起点），401 逻辑由 IAM 直接返回（密码错误/验证码错误），区别于后续受保护端点的网关级 401。补充错误响应（400/401/423/429）。新增"认证链路说明"块，阐明本端点与 §5.1 JWT 签发流程的衔接关系。 |
| 问题 2（中等·MQTT GPS 字段命名未统一）：AlertTriggeredEvent、TripStatus、OverrideSignal 的 GPS 字段仍命名为 `gps`（v10 仅修改了 SafetyAlertEvent） | **修改** — 统一采用方案 (a)：将 §2.2 TripStatus JSON Schema（`trip/status/up`）、OverrideSignal JSON Schema（`driver/override/up`）中的 `gps` 字段重命名为 `gpsLocation`；将 §2.2 次要 Payload 表中 AlertTriggeredEvent 的 `gps` 字段重命名为 `gpsLocation`。至此全文档所有 MQTT Payload 的 GPS 字段均统一为 `gpsLocation`，与 REST 契约一致。 |
| 问题 3（中等·跨文档 DTO 不一致）：`RequestMediaSessionResponse` 跨层缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`，根源在 `docs/ood_application.md` | **保留（无需修改本文件）** — API OOD §1.3 `RequestMediaSessionResponse` JSON 示例（4 字段）及 §4.1 ArkTS `RequestMediaSessionResp` 接口均已包含完整的 `sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken` 四个字段，本文档的接口契约定义是完整的。问题根源在 `docs/ood_application.md` §4.3 的同名 DTO 仅定义 2 个字段，需在应用层 OOD 中补充后两者。 |
| 问题 4（中等·跨文档 DTO 不一致）：`RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 跨层缺少 `secondaryAuthToken`，根源在 `docs/ood_application.md` | **保留（无需修改本文件）** — API OOD §1.3 中 `RequestMediaSessionRequest`（含 `secondaryAuthToken` 字段）和 `TriggerManualRescueRequest`（含 `secondaryAuthToken` 字段）的 JSON 示例均已定义完整。§4.1 ArkTS 相应接口亦同步包含该字段。问题根源在 `docs/ood_application.md` §4.3 的同名 DTO 缺失该字段，需在应用层 OOD 中补充。 |
| 问题 5（一般·S3 家属权限管理缺少主动管理入口）：仅提供 GET 查询端点，无 DELETE 撤销或请求续期入口 | **修改** — 采用方案 (b)：在 §1.3 `QueryGuardianshipPermissionsResponse` 段落后新增"设计理由"说明块，从四个维度阐述仅提供 GET 端点的设计依据：(1) 授权模型单向性（权限由系统侧事件驱动授予）；(2) 撤销驱动来源非家属侧（由系统事件或驾驶员 HMI 操作触发）；(3) DELETE 端点语义与授权模型冲突；(4) 合规与安全考量（隐私敏感资源需配套审计/通知机制）。结论为当前仅提供查询端点，未来若需求明确要求家属主动撤销，可扩展 DELETE 端点。 |
| 问题 6（一般·跨文档 StatusColor 枚举 `ORANGE` 值不一致）：应用层 OOD 含四值 `ORANGE`，领域层和 API OOD 均为三值 | **保留（无需修改本文件）** — API OOD §1.1 `derivedStatusColor` 字段已明确三值枚举（`GREEN`/`YELLOW`/`RED`），与领域层 VO-15 `StatusColor` 定义一致（v8 已添加一致性注解）。§4.1 ArkTS `StatusColor` 类型亦为三值。问题根源在 `docs/ood_application.md` 仍含 `ORANGE` 四值，需在应用层 OOD 中移除 `ORANGE` 或统一为三值。 |
| 问题 7（一般·跨文档 DTO 字段缺失）：(a) `AlertSummary.gpsLocation` — API OOD §1.1/§4.1 已定义，应用层 ODD 缺失；(b) `QueryTrajectoryResponse.dataConsistency` — API OOD §1.4 已定义，应用层 ODD 缺失 | **保留（无需修改本文件）** — (a) API OOD §1.1 `QueryAlertHistoryResponse` 的 alert 条目含 `gpsLocation`（第 53 行），§4.1 ArkTS `AlertSummary` 接口含 `gpsLocation?: GeoPoint`；(b) API OOD §1.4 `QueryTrajectoryResponse` 含 `dataConsistency`（第 392 行，枚举 `CONSISTENT`/`INCONSISTENT`）。本文档接口契约定义完整，问题根源均在 `docs/ood_application.md` 对应 DTO 缺失上述字段，需在应用层 OOD 中补充。 |
| 问题 8（一般·§3.2 豁免触发条件表述不精确）：当前表述为"LIFE_DETECTION 类型告警持续异常 ≥60s"，但 LifeDetectionService 在熄火落锁后 60 秒判定窗口内一次性产出 LifeDetectedEvent，不存在"持续异常"概念 | **修改** — 将 §3.2 豁免触发条件中的表述修正为两块独立触发条件：(1) `COLLISION_DISABILITY` 类型告警（碰撞失能，由 S1 RiskMonitoringService 边缘侧判定产生）；(2) `LifeDetectedEvent`（由 LifeDetectionService 在熄火落锁后 60 秒判定窗口内一次性产出）。两者均触发 `emergency_activation` 事件。移除"持续异常 ≥60s"的计时描述，改为引用具体领域事件。 |

## 修订说明（v12）

以下变更基于审查文件（a_v12_iteration_requirement.md）的 7 个 LOCATED 问题：

| 审查意见 | 处理方式 |
|---------|---------|
| 问题 1（中等·认证链路不完整）：JWT refresh token 端点缺失 | **修改** — 在 §1.7 Auth 端点表中新增 `POST /api/v1/auth/refresh` 端点及 `RefreshTokenRequest`/`RefreshTokenResponse` DTO，采用 token 轮换策略（旧 refresh token 同时失效）。在认证链路说明块中补充 refresh 端点的定位与使用时机说明。更新错误响应块标题为"login / refresh"并补充 refresh 相关错误码。 |
| 问题 2（中等·错误响应不完整）：S5 和 S6 错误响应列表缺少基础错误码，S3 同样缺失 `400`/`404` | **修改** — §1.5 S5 补充 `400`（查询参数无效）和 `404`（驾驶员/车辆不存在）；§1.6 S6 补充 `404`（车辆/任务不存在）和 `503`（IoTDA 通道不可达，`IoTDAChannelFailure`）；§1.3 S3 补充 `400`（请求参数无效）和 `404`（驾驶员/车辆不存在）。参考基线为 S1（400/404/503），横向核对 S1–S6 全部服务确保基础错误码覆盖一致性。 |
| 问题 3（一般·认证链路）：LoginRequest JSON 字段设计冗余 | **修改** — 采用方案 (a)：移除 `LoginRequest` JSON 示例中冗余的 `phone`/`smsCode` 字段，仅保留 `credential`+`secret`+`authMethod` 三字段。两种认证模式共用同组字段，`credential`/`secret` 的语义由 `authMethod` 决定。更新字段说明文本明确此语义分配。 |
| 问题 4（一般·API 契约完整性）：REST 错误响应体结构未定义 | **修改** — 在 §一 总述段落后新增"REST 错误响应体约定"块（与 DELETE 响应码约定、401 统一处理约定并列），定义统一格式 `{ "errorCode", "message", "requestId" }`，明确 `errorCode` 映射到应用层 `AppError` 枚举变体名，并给出完整 `AppError` 取值列表。 |
| 问题 5（一般·完整性）：S4 绩效预警订阅缺少取消订阅端点 | **修改** — 在 §1.4 S4 端点表中新增 `DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId}` 端点，返回 `204 No Content`，与 S3 的 `POST/DELETE media-session` 对称设计一致。 |
| 问题 6（轻微·安全设计不完整）：令牌桶容量参数未定义 | **修改** — 在 §5.3 接口限流策略表中新增"桶容量"列，为各限流层级补充容量规格：全局 1500、家属 45、管理员 90、救援 30、高敏操作 5、报告生成 8、MQTT 上报 150。更新说明文字阐明桶容量与 1.5 倍突发容忍度的关系。 |
| 问题 7（轻微·错误响应边界不一致）：S2 `404` 语义与其他服务不一致（200 OK + 空集合 vs 404） | **修改** — 采用方案 (a)：将 S2 的行为由"tripId 不存在时返回 200 OK + 空集合"改为返回 `404`，与 S1/S4 对不存在资源的处理保持一致。新增 S2 `404` 设计理由注释块说明此决策及未来若需防信息泄露可回退为 200 的扩展路径。 |

