# 车载安全监测系统 API 规范文档

> 基于代码实际实现（`code/server/src/main/java/com/aiot/interfaces/`），2026-07-04 核对。
> 基准路径：`http://{host}:8080`

---

## 目录

- [一、通用约定](#一通用约定)
- [二、REST API](#二rest-api)
  - [2.1 Auth — 认证](#21-auth--apiv1auth)
  - [2.2 Account — 账户](#22-account--apiv1account)
  - [2.3 Driver — 驾驶员 CRUD](#23-driver--apiv1driver)
  - [2.4 Drivers — 驾驶员风险查询](#24-drivers--apiv1drivers)
  - [2.5 Vehicles — 车辆](#25-vehicles--apiv1vehicles)
  - [2.6 Safety — 安全数据查询](#26-safety--apiv1safety)
  - [2.7 Health — 健康档案](#27-health--apiv1health)
  - [2.8 Guardianship — 家属监护](#28-guardianship--apiv1guardianship)
  - [2.9 Fleet — 车队管理](#29-fleet--apiv1fleet)
  - [2.10 Projection — 大屏投影](#210-projection--apiv1projection)
  - [2.11 SparkRTC — 实时音视频](#211-sparkrtc--apiv1sparkrtc)
  - [2.12 Storage — 文件存储](#212-storage--apiv1storage)
- [三、WebSocket 通信](#三websocket-通信)
- [四、MQTT 设备通信](#四mqtt-设备通信)
- [五、认证与授权](#五认证与授权)
- [六、错误码](#六错误码)
- [七、已知问题](#七已知问题)

---

## 一、通用约定

### 1.1 请求头

| 头名称 | 必填 | 说明 |
|--------|:----:|------|
| `Authorization` | 是* | `Bearer {accessToken}` |
| `Content-Type` | 是 | `application/json`（文件上传除外） |

> *公开端点（`/api/v1/auth/login`、`/api/v1/auth/refresh`）和 WebSocket 握手（`/ws/**`）不需要此头。

### 1.2 错误响应体格式

所有非 2xx 响应统一返回：

```json
{
  "errorCode": "NotFound",
  "message": "人类可读错误描述",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `errorCode` | string | 机器可读错误码，用于客户端分支处理 |
| `message` | string | 人类可读错误描述 |
| `requestId` | string | UUID v4，用于日志关联和问题排查 |

### 1.3 分页约定

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 0 | 页码（从 0 开始） |
| `size` | int | 10 | 每页条数 |

> 注意：`drivers/{driverId}/alerts` 和 `fleet/{fleetId}/high-risk-drivers` 的 `page` 默认值不同（见各端点说明）。

### 1.4 时间格式

所有日期时间字段统一使用 ISO 8601 格式：
```
2026-06-29T08:30:00
```

### 1.5 JWT Token 结构

登录成功后返回：

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | 访问令牌，有效期 3600s |
| `refreshToken` | string | 刷新令牌，用于 renew accessToken |
| `tokenType` | string | 固定为 `"Bearer"` |
| `expiresIn` | int | accessToken 有效期（秒） |

---

## 二、REST API

### 2.1 Auth — `/api/v1/auth`

认证端点，无需 Authorization 头。

---

#### `POST /api/v1/auth/login`

登录获取 JWT。

**请求体**

```json
{
  "authMethod": "password",
  "credential": "13900000001",
  "secret": "hashed_password_123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `authMethod` | string | — | 认证方式（当前忽略，默认密码认证） |
| `credential` | string | 是 | 手机号或用户名 |

支持的用户名快捷登录：
| 用户名 | 映射手机号 | 角色 |
|--------|-----------|------|
| `family001` | 13900000001 | FAMILY |
| `family002` | 13900000002 | FAMILY |
| `manager001` | 18800000001 | MANAGER |

| `secret` | string | 是 | 密码（BCrypt 哈希匹配） |

**成功响应** `200`

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 401 | `AuthFailed` | 账户不存在或密码错误 |

---

#### `POST /api/v1/auth/refresh`

刷新 accessToken。

**请求体**

```json
{
  "refreshToken": "eyJ..."
}
```

**成功响应** `200`

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 401 | `TokenInvalid` | refreshToken 无效、过期或类型不匹配 |

---

#### `POST /api/v1/auth/secondary-verify`

二次身份验证（OTP）。

**请求体**

```json
{
  "accountId": "acct-001",
  "method": "sms",
  "otp": "123456"
}
```

**成功响应** `200`

```json
{
  "secondaryAuthToken": "secondary-a1b2c3d4-...",
  "expiresAt": "2026-06-29T09:00:00Z"
}
```

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 400 | `VerificationFailed` | 验证码不正确 |

---

### 2.2 Account — `/api/v1/account`

账户查询。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/account/list`

返回全部系统账户列表。

**成功响应** `200`

```json
[
  {
    "accountId": "acct-001-aaa-bbb-ccc-111111111111",
    "phone": "13900000001",
    "role": "FAMILY",
    "passwordHash": "$2a$10$..."
  },
  {
    "accountId": "acct-003-aaa-bbb-ccc-333333333333",
    "phone": "18800000001",
    "role": "MANAGER",
    "passwordHash": "$2a$10$..."
  }
]
```

> **注意**：响应体直接暴露 JPA Entity，包含 `passwordHash` 字段。生产环境应使用 DTO 脱敏。

---

#### `GET /api/v1/account/{phone}`

按手机号查找账户。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `phone` | string | 11 位手机号 |

**成功响应** `200`

返回单个 `SystemAccountJpaEntity`，结构同列表项。未找到时返回 `null` 体（HTTP 200）。

---

### 2.3 Driver — `/api/v1/driver`

驾驶员 CRUD。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/driver/list`

驾驶员列表。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `name` | string | 否 | 按姓名模糊过滤 |

**成功响应** `200`

```json
[
  {
    "driverId": { "id": "d001-a1b2-c3d4-e5f6-123456789abc" },
    "name": "张三",
    "phone": "13800000001"
  }
]
```

---

#### `POST /api/v1/driver`

创建驾驶员。若请求体中未提供 `driverId`，后端自动生成。

**请求体**

```json
{
  "name": "张三",
  "phone": "13800000001"
}
```

**成功响应** `200`

返回创建的 `Driver` 对象。

---

#### `PUT /api/v1/driver`

更新驾驶员。

**请求体**

```json
{
  "driverId": { "id": "d001-a1b2-c3d4-e5f6-123456789abc" },
  "name": "张三（更新）",
  "phone": "13800000001"
}
```

**成功响应** `200`

返回更新后的 `Driver` 对象。

---

#### `DELETE /api/v1/driver/{id}`

删除驾驶员。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | string | 驾驶员 ID |

**成功响应** `204 No Content`（无响应体）

---

### 2.4 Drivers — `/api/v1/drivers`

驾驶员风险状态与告警历史查询。

**权限**：
- `GET /{driverId}/risk-status` — 公开（permitAll）
- `GET /{driverId}/alerts` — 已认证

---

#### `GET /api/v1/drivers/{driverId}/risk-status`

查询驾驶员当前风险状态。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `driverId` | string | 驾驶员 ID |

**成功响应** `200`

```json
{
  "hasActiveTrip": true,
  "activeAlerts": [
    { "alertType": "FATIGUE", "riskLevel": "L2_WARNING" },
    { "alertType": "DISTRACTION", "riskLevel": "L1_HINT" }
  ],
  "derivedStatusColor": "YELLOW"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `hasActiveTrip` | boolean | 是否有活跃行程 |
| `activeAlerts` | array | 当前活跃风险列表 |
| `alertType` | string | 告警类型 |
| `riskLevel` | string | 风险等级 |
| `derivedStatusColor` | string | 综合状态色：`GREEN` / `YELLOW` / `RED` |

**告警类型枚举**: `FATIGUE`, `DISTRACTION`, `ROAD_RAGE`, `COLLISION_DISABILITY`, `LIFE_DETECTION`, `PERFORMANCE_WARNING`

**风险等级枚举**: `L1_HINT`, `L2_WARNING`, `L3_CRITICAL`

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 404 | `NotFound` | 驾驶员不存在 |
| 403 | `AccessDenied` | 权限不足 |
| 409 | `InvalidState` | 状态冲突 |

---

#### `GET /api/v1/drivers/{driverId}/alerts`

查询驾驶员历史告警。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `driverId` | string | 驾驶员 ID |

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `from` | string | 否 | — | 起始时间 (ISO 8601) |
| `to` | string | 否 | — | 截止时间 (ISO 8601) |
| `alertType` | string | 否 | — | 告警类型 |
| `riskLevel` | string | 否 | — | 风险等级 |
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**成功响应** `200`

```json
{
  "alerts": [
    {
      "alertId": "alert-uuid-001",
      "alertType": "FATIGUE",
      "riskLevel": "L3_CRITICAL",
      "occurredAt": "2026-06-29T08:30:00",
      "resolvedAt": null,
      "tripId": "trip-uuid-042",
      "gpsLocation": null
    }
  ],
  "totalCount": 42
}
```

> **注意**：当前实现中 `resolvedAt` 和 `gpsLocation` 固定为 `null`。

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 404 | `NotFound` | 驾驶员不存在 |
| 403 | `AccessDenied` | 权限不足 |
| 409 | `InvalidState` | 状态冲突 |

---

### 2.5 Vehicles — `/api/v1/vehicles`

车辆相关查询。

**权限**：FAMILY

---

#### `GET /api/v1/vehicles/{vehicleId}/windows`

查询车窗状态。

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| `vehicleId` | string | 车辆 ID |

**成功响应** `200`

```json
{
  "windowStatuses": [
    {
      "windowPosition": "FRONT_LEFT",
      "state": "CLOSED",
      "lastOperation": "CLOSE",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:00"
    },
    {
      "windowPosition": "FRONT_RIGHT",
      "state": "CLOSED",
      "lastOperation": "CLOSE",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:00"
    },
    {
      "windowPosition": "REAR_LEFT",
      "state": "OPEN",
      "lastOperation": "OPEN",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:00"
    },
    {
      "windowPosition": "REAR_RIGHT",
      "state": "CLOSED",
      "lastOperation": "CLOSE",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:00"
    }
  ]
}
```

**车窗位置枚举**: `FRONT_LEFT`, `FRONT_RIGHT`, `REAR_LEFT`, `REAR_RIGHT`

**车窗状态枚举**: `OPEN`, `CLOSED`, `OPENING`, `CLOSING`

> **注意**：当前实现返回硬编码数据，未接入真实车窗状态服务。

---

### 2.6 Safety — `/api/v1/safety`

安全数据查询。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/safety/trip/list`

行程列表。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `driverId` | string | 否 | 驾驶员 ID |
| `active` | boolean | 否 | 是否仅查询活跃行程 |

**成功响应** `200`

```json
[
  {
    "tripId": { "id": "trip-uuid-001" },
    "driverId": { "id": "d001..." },
    "vehicleId": { "id": "v001..." },
    "startTime": "2026-06-29T08:00:00",
    "endTime": null,
    "status": "ACTIVE"
  }
]
```

---

#### `GET /api/v1/safety/alert/list`

告警列表。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `driverId` | string | 否 | 驾驶员 ID |
| `riskLevel` | string | 否 | 风险等级 |
| `alertType` | string | 否 | 告警类型 |

**成功响应** `200`

返回 `SafetyAlertEvent` 数组。

---

#### `GET /api/v1/safety/vehicle/list`

车辆列表。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `fleetId` | string | 否 | 车队 ID |
| `keyword` | string | 否 | 关键词搜索 |

**成功响应** `200`

返回 `Vehicle` 数组。

---

### 2.7 Health — `/api/v1/health`

驾驶员健康档案。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/health/{driverId}`

获取健康档案。

**成功响应** `200`

```json
{
  "driverId": "d001...",
  "bloodType": "A",
  "chronicConditions": "高血压",
  "emergencyContact": "13800000002",
  "updatedAt": "2026-06-29T08:00:00"
}
```

未找到时返回 `null`（HTTP 200）。

---

#### `PUT /api/v1/health/{driverId}`

更新健康档案。

**请求体**

```json
{
  "bloodType": "A",
  "chronicConditions": "高血压、糖尿病",
  "emergencyContact": "13800000002"
}
```

**成功响应** `200`

返回更新后的 `DriverHealthProfileEntity`。

---

### 2.8 Guardianship — `/api/v1/guardianship`

家属远程监护。

**权限**：FAMILY

---

#### `GET /api/v1/guardianship/list`

监护绑定列表。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `driverId` | string | 否 | 按驾驶员过滤 |
| `accountId` | string | 否 | 按账户过滤 |

**成功响应** `200`

```json
[
  {
    "driverId": "d001...",
    "accountId": "acct-001...",
    "status": "ACTIVE",
    "establishedAt": "2026-06-20T10:00:00"
  }
]
```

---

#### `POST /api/v1/guardianship`

创建监护绑定（CRUD 风格）。

**请求体**

```json
{
  "driverId": "d001...",
  "accountId": "acct-001..."
}
```

**成功响应** `200`

返回创建的 `GuardianshipEntity`。

---

#### `DELETE /api/v1/guardianship/{driverId}/{accountId}`

撤销监护绑定。

**成功响应** `204 No Content`

---

#### `POST /api/v1/guardianship/bind`

家属绑定驾驶员（前端兼容端点）。

**请求体**

```json
{
  "familyAccountId": "acct-001",
  "driverId": "d001..."
}
```

> `familyAccountId` 字段当前忽略，实际使用 JWT 中的 accountId。

**成功响应** `200`

```json
{
  "status": "bound",
  "accountId": "acct-001...",
  "driverId": "d001..."
}
```

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 404 | `NotFound` | 驾驶员不存在 |
| 403 | `AccessDenied` | 权限不足 |
| 409 | `InvalidState` | 状态冲突 |

---

#### `POST /api/v1/guardianship/media-session`

请求音视频对讲会话（通过 SparkRTC）。

**请求体**

```json
{
  "familyAccountId": "acct-001",
  "driverId": "d001...",
  "sessionType": "AUDIO",
  "secondaryAuthToken": "secondary-..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `sessionType` | string | 是 | `AUDIO` / `VIDEO` |
| `secondaryAuthToken` | string | — | 二次验证 token（当前未校验） |

**成功响应** `200`

```json
{
  "sessionHandle": "b5f3a2e1-...",
  "sessionToken": "c8d4e5f6-...",
  "sparkRTCRoomId": "room-a1b2c3d4",
  "sparkRTCJoinToken": "f9e8d7c6-..."
}
```

> `sparkRTCJoinToken` 当前为随机 UUID，非真实 SparkRTC token。

**错误响应**

| 状态码 | errorCode | 场景 |
|:------:|-----------|------|
| 404 | `NotFound` | 驾驶员不存在 |
| 403 | `AccessDenied` | 权限不足 |
| 409 | `InvalidState` | 状态冲突 |

---

#### `DELETE /api/v1/guardianship/media-session/{sessionHandle}`

结束音视频对讲会话。

**成功响应** `204 No Content`

---

#### `PUT /api/v1/guardianship/notification-preference`

更新通知偏好。

**请求体**

```json
{
  "familyAccountId": "acct-001",
  "driverId": "d001...",
  "preferredRiskLevels": ["L2_WARNING", "L3_CRITICAL"]
}
```

**成功响应** `200`

```json
{
  "status": "updated"
}
```

---

#### `POST /api/v1/guardianship/manual-rescue`

手动触发救援。

**请求体**

```json
{
  "familyAccountId": "acct-001",
  "driverId": "d001...",
  "secondaryAuthToken": "secondary-..."
}
```

> `secondaryAuthToken` 当前忽略。

**成功响应** `200`

```json
{
  "rescueRequestId": "rescue-req-a1b2c3d4",
  "rescueReportId": "rescue-uuid-...",
  "status": "PENDING"
}
```

---

#### `POST /api/v1/guardianship/window-control`

远程控制车窗。

**请求体**

```json
{
  "familyAccountId": "acct-001",
  "driverId": "d001...",
  "windowOperation": "CLOSE",
  "windowPosition": "REAR_LEFT",
  "secondaryAuthToken": "secondary-..."
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `windowOperation` | string | — | `OPEN` / `CLOSE`（当前忽略） |
| `windowPosition` | string | 是 | 车窗位置 |
| `secondaryAuthToken` | string | — | 二次验证 token（当前忽略） |

**成功响应** `200`

```json
{
  "status": "controlled",
  "windowPosition": "REAR_LEFT"
}
```

---

#### `GET /api/v1/guardianship/{driverId}/permissions`

查询家属对指定驾驶员的监护权限。

**成功响应** `200`

```json
{
  "familyAccountId": "acct-001...",
  "driverId": "d001...",
  "permissions": [
    {
      "permissionType": "STATUS_SUBSCRIBE",
      "granted": true,
      "grantedAt": "2026-06-29T08:30:00",
      "expiresAt": null
    },
    {
      "permissionType": "MEDIA_SESSION",
      "granted": true,
      "grantedAt": "2026-06-29T08:30:00",
      "expiresAt": null
    }
  ],
  "careRelationship": {
    "status": "ACTIVE",
    "establishedAt": "2026-06-29T08:30:00"
  }
}
```

| careRelationship.status | 说明 |
|--------------------------|------|
| `ACTIVE` | 监护关系有效 |
| `REVOKED` | 监护关系已撤销 |

---

### 2.9 Fleet — `/api/v1/fleet`

车队管理。

**权限**：MANAGER

---

#### `GET /api/v1/fleet/{fleetId}/fatigue-distribution`

获取车队疲劳分布与热力图数据。

**成功响应** `200`

```json
{
  "distribution": {
    "L1_HINT": 0.45,
    "L2_WARNING": 0.35,
    "L3_CRITICAL": 0.20
  },
  "heatmapData": [
    { "latitude": 39.90, "longitude": 116.40, "riskIntensity": 0.15 },
    { "latitude": 39.91, "longitude": 116.41, "riskIntensity": 0.30 },
    { "latitude": 39.92, "longitude": 116.39, "riskIntensity": 0.08 }
  ],
  "dataFreshness": "2026-06-29T08:30:00",
  "generatedAt": "2026-06-29T08:30:00"
}
```

> **注意**：`heatmapData` 当前为硬编码数据。

---

#### `GET /api/v1/fleet/{fleetId}/offline-vehicles`

离线车辆列表。

**成功响应** `200`

```json
{
  "offlineVehicles": [
    {
      "vehicleId": "v004...",
      "licensePlate": "京A12345",
      "driverId": "d004...",
      "driverName": "Driver-d004",
      "offlineReason": "SENSOR_FAULT",
      "offlineSince": "2026-06-29T08:30:00",
      "lastHeartbeat": "2026-06-29T08:30:00"
    }
  ]
}
```

---

#### `GET /api/v1/fleet/{fleetId}/trajectory`

车辆轨迹查询。

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `vehicleId` | string | 否 | `v001-c7e5-4ghi-a011-678901234hij` | 车辆 ID |
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 100 | 每页条数 |

**成功响应** `200`

```json
{
  "trajectoryPoints": [
    {
      "timestamp": "2026-06-29T08:30:00",
      "latitude": 39.9080,
      "longitude": 116.4100,
      "speed": 45.5
    }
  ],
  "totalCount": 120,
  "dataConsistency": "CONSISTENT"
}
```

> 若未指定 `vehicleId`，使用默认值 `v001-c7e5-4ghi-a011-678901234hij`。

---

#### `GET /api/v1/fleet/{fleetId}/high-risk-drivers`

高危驾驶员下钻。

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `riskLevel` | string | 否 | `L3_CRITICAL` | 风险等级 |
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 10 | 每页条数 |

**成功响应** `200`

```json
{
  "drivers": [
    {
      "driverId": "d001...",
      "driverName": "张三",
      "compositeRiskScore": 85.0,
      "latestTripSummary": {
        "tripId": "trip-d001",
        "startTime": "2026-06-29T05:30:00",
        "endTime": "2026-06-29T07:30:00",
        "score": 85.0
      },
      "primaryPenaltyItems": ["急刹车", "疲劳驾驶"]
    }
  ],
  "totalCount": 5
}
```

> `latestTripSummary` 的 `tripId` 为 mock 数据（`"trip-"` + driverId 前 4 位）。

---

#### `POST /api/v1/fleet/reports`

生成驾驶行为报告。

**请求体**

```json
{
  "driverId": "d001...",
  "timeRange": {
    "start": "2026-06-01T00:00:00",
    "end": "2026-06-29T23:59:59"
  },
  "reportType": "DRIVING_BEHAVIOR"
}
```

**成功响应** `200`

```json
{
  "reportId": "report-uuid-001",
  "reportData": {
    "reportId": "report-uuid-001",
    "driverId": "d001...",
    "timeRange": {
      "start": "2026-06-01T00:00:00",
      "end": "2026-06-29T23:59:59"
    },
    "reportType": "DRIVING_BEHAVIOR",
    "drivingBehaviorSummary": {
      "overallScore": 85.0,
      "subScores": {
        "fatigueScore": 80.0,
        "distractionScore": 75.0,
        "abnormalDrivingScore": 90.0
      },
      "trendVsLastPeriod": 2.5
    },
    "riskDistribution": {
      "FATIGUE": 0.4,
      "DISTRACTION": 0.3,
      "ROAD_RAGE": 0.3
    },
    "penaltyBreakdown": [
      {
        "category": "急刹车",
        "penaltyScore": 10.0,
        "topViolations": ["2025-12-20 08:30"]
      }
    ],
    "totalMileage": 120.5,
    "totalDrivingTime": "PT2H30M",
    "generatedAt": "2026-06-29T08:30:00"
  },
  "downloadUrl": "/api/v1/fleet/reports/report-uuid-001/download?format=json",
  "isEmpty": false
}
```

> **注意**：`drivingBehaviorSummary`、`riskDistribution`、`penaltyBreakdown` 中 `overallScore` 及各项数据均为硬编码模拟值。

---

#### `GET /api/v1/fleet/reports/{reportId}/download`

下载报告。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `format` | string | 是 | `pdf` 或 `json` |

**成功响应** `200`

返回文件流。响应头：
- `format=pdf`: `Content-Type: application/pdf`
- `format=json`: `Content-Type: application/json`
- `Content-Disposition: attachment; filename="{reportId}.{format}"`

> **注意**：当前无论什么 `reportId`，均返回一个包含 reportId 和生成时间的简单 JSON（即使是 `format=pdf`）。

---

#### `POST /api/v1/fleet/performance-warning-subscription`

订阅绩效预警。

**请求体**

```json
{
  "adminId": "acct-003...",
  "fleetId": "fleet-east-1"
}
```

**成功响应** `200`

```json
{
  "subscriptionId": "sub-uuid-001"
}
```

---

#### `DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId}`

取消绩效预警订阅。

**成功响应** `204 No Content`

> **注意**：当前实现体为空，未实际执行取消操作。

---

### 2.10 Projection — `/api/v1/projection`

大屏投影数据只读查询。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/projection/alert`

告警投影数据。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `fleetId` | string | 否 | 车队 ID |
| `riskLevel` | string | 否 | 风险等级 |
| `activeOnly` | boolean | 否 | 是否仅活跃告警 |

**成功响应** `200`

返回 `AlertProjectionEntity` 数组。

> **注意**：`activeOnly=true` 时当前返回 `null`，未实际实现活跃告警过滤。

---

#### `GET /api/v1/projection/dashboard`

仪表盘投影数据。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `fleetId` | string | 否 | 车队 ID |

**成功响应** `200`

返回 `FleetDashboardProjectionEntity` 数组。

---

#### `GET /api/v1/projection/trajectory`

轨迹投影数据。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `tripId` | string | 是 | 行程 ID |

**成功响应** `200`

返回 `TrajectoryProjectionEntity` 数组。

---

### 2.11 SparkRTC — `/api/v1/sparkrtc`

华为 SparkRTC 实时音视频 Token 管理。

**权限**：FAMILY

---

#### `POST /api/v1/sparkrtc/token`

签发 SparkRTC room token。

**请求体**

```json
{
  "roomId": "room-001",
  "userId": "user-001",
  "role": "joiner"
}
```

**成功响应** `200`

```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": "2026-06-29T08:40:00Z"
}
```

> **注意**：当前 `token` 为随机 UUID，非真实华为 SparkRTC Token。生产环境需对接 SparkRTC SDK 签发真实 token。

---

### 2.12 Storage — `/api/v1/storage`

本地文件存储。

**权限**：已认证（任意角色）

---

#### `GET /api/v1/storage/info`

存储配置信息。

**成功响应** `200`

```json
{
  "basePath": "./data/storage",
  "maxVoiceFileSizeMb": 50,
  "maxOtaFileSizeMb": 500,
  "voiceExpiryDays": 30
}
```

---

#### `GET /api/v1/storage/list`

文件列表。

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `dir` | string | 否 | `voice` | 目录名 |

**成功响应** `200`

```json
["file1.wav", "file2.wav"]
```

---

#### `POST /api/v1/storage/upload`

上传文件。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `dir` | string | 是 | 目标目录 |
| `fileName` | string | 是 | 文件名 |

**请求体**: 二进制字节流（`application/octet-stream` 或 `application/json`）

**成功响应** `200`

```json
{
  "status": "stored",
  "path": "voice/file1.wav"
}
```

---

## 三、WebSocket 通信

### 3.1 端点

| 端点 | 角色限制 | 说明 |
|------|----------|------|
| `ws://{host}:8080/ws/guardianship?token={jwt}` | FAMILY | 家属 APP 实时推送 |
| `ws://{host}:8080/ws/fleet?token={jwt}` | MANAGER | 车队大屏实时推送 |

### 3.2 认证

WebSocket 握手不受 Spring Security 拦截（`/ws/**` 设为 `permitAll`）。认证在 Handler 层通过 URL 查询参数 `token` 中的 JWT 完成：
- Token 中携带 `accountId` 和 `role`
- 角色不符（如 FAMILY 连接 `/ws/fleet`）将导致连接立即关闭

支持测试用 `mock_token`：`/ws/guardianship` 默认映射到 `acct-001`（FAMILY），`/ws/fleet` 默认映射到 `acct-003`（MANAGER）。

### 3.3 客户端→服务端 消息类型

所有消息为 JSON 文本帧，顶层必须包含 `type` 字段。

#### `/ws/guardianship`（家属 APP）

| type | 必填字段 | 说明 |
|------|----------|------|
| `subscribe_status` | `driverId` | 订阅驾驶员状态快照 |
| `unsubscribe_status` | `driverId` | 取消订阅 |
| `request_media` | `driverId`, `sessionType` | 请求音视频对讲（`sessionType`: `AUDIO`/`VIDEO`） |
| `end_media` | `sessionHandle` | 挂断对讲 |
| `renew_token` | `sessionHandle` | 续签 SparkRTC Token |
| `trigger_rescue` | `driverId` | 手动触发救援 |
| `pong` | — | 心跳响应 |

#### `/ws/fleet`（车队大屏）

| type | 必填字段 | 说明 |
|------|----------|------|
| `subscribe_fleet` | `fleetId` | 订阅车队告警 |
| `pong` | — | 心跳响应 |

### 3.4 服务端→客户端 消息类型

#### 通用

```json
{
  "type": "connection_established",
  "payload": {
    "connectionId": "uuid",
    "accountId": "acct-001..."
  }
}
```

```json
{
  "type": "ping",
  "payload": { "serverTime": "2026-06-29T08:30:00Z" }
}
```

```json
{
  "type": "error",
  "code": "AUTH_FAILED",
  "message": "认证失败或权限不足"
}
```

#### 家属 APP 专用

**状态快照（≥1Hz 推送）**
```json
{
  "type": "driver_status_snapshot",
  "driverId": "d001...",
  "vehicleId": "v001...",
  "timestamp": "2026-06-29T08:30:00Z",
  "activeAlertLevels": { "FATIGUE": "L2_WARNING" },
  "gpsLocation": { "latitude": 39.9080, "longitude": 116.4100 },
  "speed": 45.5,
  "tripStatus": "ACTIVE",
  "physiologicalSummary": {
    "heartRate": 72.0,
    "spo2": 98.0,
    "emotionIndex": 0.3
  },
  "windowStatus": [
    {
      "windowPosition": "FRONT_LEFT",
      "state": "CLOSED",
      "lastOperation": "CLOSE",
      "lastOperationResult": "SUCCESS",
      "updatedAt": "2026-06-29T08:30:00Z"
    }
  ]
}
```

**告警推送**
```json
{
  "type": "alert_triggered",
  "alertId": "alert-uuid-001",
  "alertType": "FATIGUE",
  "riskLevel": "L3_CRITICAL",
  "occurredAt": "2026-06-29T08:30:00Z",
  "resolvedAt": null,
  "tripId": "trip-uuid-042",
  "gpsLocation": null
}
```

**权限授予（含 SparkRTC 入会凭证）**
```json
{
  "type": "access_granted",
  "driverId": "d001...",
  "sessionToken": "uuid",
  "sparkRTCRoomId": "room-xxx",
  "sparkRTCJoinToken": "uuid",
  "reason": "REGULAR_60S"
}
```

**权限撤销**
```json
{
  "type": "access_revoked",
  "driverId": "d001...",
  "reason": "SESSION_EXPIRED"
}
```

**订阅确认**
```json
{
  "type": "subscribe_status_ack",
  "subscriptionId": "uuid",
  "initialSnapshot": null
}
```

**救援触发确认**
```json
{
  "type": "rescue_triggered",
  "rescueRequestId": "uuid",
  "rescueReportId": "uuid",
  "status": "PENDING"
}
```

**Token 续签**
```json
{
  "type": "token_renewed",
  "sparkRTCRoomId": "room-xxx",
  "sparkRTCJoinToken": "new-token-uuid",
  "expiresAt": "2026-06-29T08:40:00Z"
}
```

#### 车队大屏专用

**L3 高危告警**
```json
{
  "type": "l3_alert",
  "fleetId": "fleet-east-1",
  "driverId": "d001...",
  "vehicleId": "v001...",
  "alertType": "FATIGUE",
  "occurredAt": "2026-06-29T08:30:00Z",
  "gpsLocation": { "latitude": 39.9080, "longitude": 116.4100 }
}
```

**绩效预警**
```json
{
  "type": "performance_warning",
  "driverId": "d001...",
  "driverName": "张三",
  "score": 65,
  "scorePeriod": "WEEKLY",
  "primaryPenaltyItems": ["急刹车×3", "疲劳驾驶×2"],
  "occurredAt": "2026-06-29T08:30:00Z"
}
```

### 3.5 心跳与重连

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 心跳间隔 | 30s | 服务端每隔 30s 发送 `ping` |
| 连续丢失上限 | 3 次 | 超限后服务端主动断开连接 |
| 在线检测 | 10s | 等待 pong 的超时时间 |

客户端必须在收到 `ping` 后立即回复 `{"type":"pong"}`。

### 3.6 离线消息

- 家属端断线期间的告警存入 `OfflineAlertQueue`（最多保留 20 条）
- 重新连接后自动补推

---

## 四、MQTT 设备通信

Topic 模板中 `{deviceId}` 替换为车载终端序列号，`{accountId}`/`{fleetId}` 替换为对应标识。

### 4.1 上行（设备 → 云）

| Topic 模板 | QoS | 说明 |
|------------|:---:|------|
| `{deviceId}/sensor/{sensorType}/up` | 1 | 流式感知数据上报 |
| `{deviceId}/trip/status/up` | 1 | 行程状态变更 |
| `{deviceId}/alert/up` | 1 | 告警事件上报 |
| `{deviceId}/physiological/snapshot/up` | 1 | 生理体征快照 |
| `{deviceId}/vehicle/state/up` | 1 | 车辆状态遥测 |
| `{deviceId}/status/heartbeat/up` | 0 | 设备心跳 |
| `{deviceId}/sensor/fault/up` | 1 | 传感器故障 |
| `{deviceId}/sensor/occlusion/up` | 1 | 摄像头遮挡 |
| `{deviceId}/driver/override/up` | 1 | 驾驶员覆盖信号 |
| `{deviceId}/trip/score/up` | 1 | 行程评分上报 |
| `{deviceId}/voice/evidence/up` | 1 | 语音存证上传 |
| `{deviceId}/cmd/{commandId}/ack` | 1 | 指令执行确认 |

### 4.2 下行（云 → 设备）

| Topic 模板 | QoS | 说明 |
|------------|:---:|------|
| `{deviceId}/cmd/intervention/down` | 1 | 干预指令下发 |
| `{deviceId}/cmd/window/down` | 1 | 车窗控制指令 |
| `{deviceId}/cmd/door/unlock/down` | 1 | 车门解锁指令 |
| `{deviceId}/cmd/ota/down` | 1 | OTA 升级包 |
| `{deviceId}/cmd/ota/rollback/down` | 1 | OTA 回滚指令 |
| `{deviceId}/cmd/media/join/down` | 1 | SparkRTC 入房凭证 |

### 4.3 推送（云 → App/大屏）

| Topic 模板 | QoS | 说明 |
|------------|:---:|------|
| `family/{accountId}/alert/push` | 1 | 家属告警推送 |
| `family/{accountId}/status/push` | 1 | 家属状态快照推送 |
| `family/{accountId}/access/granted` | 1 | 家属权限授予推送 |
| `family/{accountId}/access/revoked` | 1 | 家属权限撤销推送 |
| `fleet/{fleetId}/alert/push` | 1 | 车队告警推送 |
| `fleet/{fleetId}/performance-warning/push` | 1 | 绩效预警推送 |
| `app/{accountId}/rescue/confirm` | 1 | SOS 确认通知 |

---

## 五、认证与授权

### 5.1 角色定义

| 角色 | 常量 | 说明 |
|------|------|------|
| FAMILY | `ROLE_FAMILY` | 家属，可进行远程监护 |
| MANAGER | `ROLE_MANAGER` | 车队管理员，可进行车队管理 |
| RESCUE | `ROLE_RESCUE` | 救援人员（当前未实现对应端点） |

### 5.2 访问控制规则

| 路径模式 | 权限 |
|----------|------|
| `/api/v1/auth/login`, `/api/v1/auth/refresh` | 公开 |
| `/ws/**` | 公开（*Handler 层自行鉴权*） |
| `/api/v1/drivers/*/risk-status` | 公开 |
| `/api/v1/drivers/**` (GET) | 已认证 |
| `/api/v1/trips/**` (GET) | 已认证 |
| `/api/v1/trips/*/interventions/active` (GET) | 公开 |
| `/api/v1/trips/*/override` (POST) | 公开 |
| `/hmi/**`, `/static/hmi/**` | 公开 |
| `/api/v1/emergency/**` | RESCUE |
| `/api/v1/fleet/**` | MANAGER |
| `/api/v1/guardianship/**` | FAMILY |
| `/api/v1/vehicles/**` | FAMILY |
| `/api/v1/sparkrtc/**` | FAMILY |
| `/api/v1/ota/**` | MANAGER |
| 其余 `/api/v1/**` | 已认证 |
| 其余所有请求 | 放行（静态资源等） |

> **注意**：`/api/v1/trips/*/interventions/active`、`/api/v1/trips/*/override`、`/api/v1/emergency/**`、`/api/v1/ota/**` 等路径当前无对应 Controller 实现。

---

## 六、错误码

### 6.1 REST 错误码

| HTTP 状态码 | errorCode | 说明 |
|:-----------:|-----------|------|
| 400 | `ValidationFailed` | 请求参数校验失败 |
| 400 | `VerificationFailed` | 验证码不正确 |
| 401 | `AuthFailed` | 登录认证失败 |
| 401 | `TokenInvalid` | Token 无效或过期 |
| 403 | `AccessDenied` | 权限不足 |
| 404 | `NotFound` | 资源不存在 |
| 409 | `InvalidState` | 业务状态冲突 |
| 500 | `InternalError` | 服务器内部错误 |

### 6.2 WebSocket 错误码

| code | 说明 |
|------|------|
| `AUTH_FAILED` | 认证失败或权限不足 |
| `PARSE_ERROR` | 消息解析失败 |
| `INVALID_REQUEST` | 缺少必填字段（如 `driverId`） |
| `SUBSCRIPTION_LIMIT` | 驾驶员订阅数已达上限（默认 3） |
| `TOKEN_RENEW_FAILED` | SparkRTC Token 续签失败 |

---

## 七、已知问题

### 7.1 严重问题

| # | 说明 | 影响 |
|---|------|------|
| 1 | `AccountController` 直接返回 JPA Entity，暴露 `passwordHash` | 安全风险 |
| 2 | `SparkRtcController.issueToken` 返回随机 UUID | 无法对接真实 SparkRTC 服务 |
| 3 | `VehiclesController.queryWindowStatus` 返回硬编码数据 | 车窗状态不反映真实情况 |

### 7.2 中等问题

| # | 说明 | 影响 |
|---|------|------|
| 4 | `FleetController` 多处 mock 数据（heatmap、trajectory、report 打分） | 数据不真实 |
| 5 | `ProjectionController.alerts` 的 `activeOnly` 参数未实现（返回 null） | 过滤失效 |
| 6 | `FleetController.unsubscribePerformanceWarning` 体为空 | 未实际取消订阅 |
| 7 | `DriversController.alerts` 中 `resolvedAt` 和 `gpsLocation` 固定为 null | 数据缺失 |

### 7.3 轻微问题

| # | 说明 | 影响 |
|---|------|------|
| 8 | `DriverController`（单数）和 `DriversController`（复数）命名不一致 | 维护困惑 |
| 9 | `GuardianshipController` 多处 `secondaryAuthToken` 未校验 | 安全缺口 |
| 10 | `SecurityConfig` 中配置了 `/api/v1/emergency/**`、`/api/v1/ota/**`、`/api/v1/trips/**` 等路径权限，但无对应 Controller | 死规则 |
| 11 | `AccountController.findByPhone` 未找到时返回 HTTP 200 + `null`，应返回 404 | REST 语义不标准 |
| 12 | `ProjectionController`、`HealthController` 缺少分页支持 | 潜在性能问题 |

### 7.4 路径实现状态

| SecurityConfig 规则 | Controller 实现 |
|---------------------|:---:|
| `/api/v1/auth/**` | ✅ AuthController |
| `/api/v1/account/**` | ✅ AccountController |
| `/api/v1/driver/**` | ✅ DriverController |
| `/api/v1/drivers/**` | ✅ DriversController |
| `/api/v1/vehicles/**` | ✅ VehiclesController |
| `/api/v1/safety/**` | ✅ SafetyController |
| `/api/v1/health/**` | ✅ HealthController |
| `/api/v1/guardianship/**` | ✅ GuardianshipController |
| `/api/v1/fleet/**` | ✅ FleetController |
| `/api/v1/projection/**` | ✅ ProjectionController |
| `/api/v1/sparkrtc/**` | ✅ SparkRtcController |
| `/api/v1/storage/**` | ✅ StorageController |
| `/api/v1/trips/**` | ❌ 无 Controller（仅 SecurityConfig 有规则） |
| `/api/v1/emergency/**` | ❌ 无 Controller |
| `/api/v1/ota/**` | ❌ 无 Controller |
| `/hmi/**` | ❌ 无 Controller |

---

*文档生成时间：2026-07-04，基于 `code/server/src/main/java/com/aiot/interfaces/` 实际代码。*
