# 任务：前端 DTO 模型 + API 客户端（角色 D）

## 依据文档

- `docs/ood_interface.md` — API/接口层 OOD 设计，§4.1 家属 APP（HarmonyOS）对接接口清单，§4.2 车队大屏对接
- `todo.md` — 分工方案中角色 D 的任务

## 任务范围

根据 `todo.md` 中的分工规划，角色 D 负责：

> **D | 前端 DTO 模型 + API 客户端 | `docs/ood_interface.md` §4.1 | `frontend/model/` 类型 + `frontend/api/`**

具体实现内容如下：

### 一、前端数据模型（`code/frontend/model/`）

基于 `docs/ood_interface.md` §4.1 中定义的 ArkTS 数据模型（DTO），创建 TypeScript/ArkTS 类型定义文件，包括：

1. **基础类型别名**（enums/type aliases）：
   - `AlertType` — FATIGUE / DISTRACTION / ROAD_RAGE / LIFE_DETECTION / COLLISION_DISABILITY / PERFORMANCE_WARNING
   - `RiskLevel` — L1_HINT / L2_WARNING / L3_CRITICAL
   - `StatusColor` — GREEN / YELLOW / RED
   - `MediaSessionType` — AUDIO / VIDEO
   - `RescueRequestStatus` — PENDING / CONFIRMED / REJECTED
   - `WindowControlOperation` — OPEN / CLOSE / PARTIAL_OPEN
   - `WindowPosition` — FRONT_LEFT / FRONT_RIGHT / REAR_LEFT / REAR_RIGHT
   - `WindowState` — OPEN / CLOSED / PARTIAL / UNKNOWN / MOVING
   - `WindowOperationResult` — SUCCESS / TIMEOUT / FAILED / PENDING
   - `GuardianshipPermissionType` — MEDIA_CALL / WINDOW_CONTROL / MANUAL_RESCUE / STATUS_MONITORING
   - `CareRelationshipStatus` — ACTIVE / SUSPENDED / REVOKED
   - `SparkRTCRole` — subscriber / publisher
   - `TripStatus` — NOT_STARTED / ACTIVE / COMPLETED
   - `AccessGrantReason` — REGULAR_60S / EMERGENCY_ACTIVATION / OCCLUSION_RECOVERY
   - `AccessRevokeReason` — RISK_DECLINED / CAMERA_OCCLUDED / DRIVER_DEACTIVATED

2. **REST 响应模型接口**：
   - `GetDriverRiskStatusResponse` + `ActiveAlertEntry`
   - `RequestMediaSessionReq` + `RequestMediaSessionResp`
   - `UpdateNotificationPreferenceReq`
   - `TriggerManualRescueReq` + `TriggerManualRescueResp`
   - `ControlVehicleWindowReq`
   - `QueryWindowStatusResp` + `WindowStatusEntry`
   - `QueryGuardianshipPermissionsResp` + `GuardianshipPermissionEntry` + `CareRelationshipSummary`
   - `IssueSparkRTCTokenReq` + `IssueSparkRTCTokenResp`
   - 认证 DTO：对应 §1.7 的 `LoginRequest` / `LoginResponse` / `RefreshTokenRequest` / `RefreshTokenResponse` / `SecondaryVerifyRequest` / `SecondaryVerifyResponse`
   - 车队大屏 DTO（§4.2）：`L3AlertMessage` / `PerformanceWarningMessage`

3. **WebSocket 消息模型**：
   - `DriverStatusSnapshot` + `GeoPoint` + `PhysiologicalDigest`
   - `AlertSummary`
   - `AccessGrantedMessage`
   - `AccessRevokedMessage`
   - `TokenRenewedMessage`
   - `SubscribeStatusAckMessage`
   - `RescueTriggeredMessage`

### 二、API 客户端（`code/frontend/api/`）

为家属 APP 和车队大屏提供类型安全的 REST API 调用封装：

1. **基础 HTTP 客户端**：
   - `ApiClient` 类 — 封装 fetch/axios，自动携带 JWT `Authorization` 头
   - `ApiResponse<T>` 通用响应包装
   - 统一错误处理，解析 `errorCode` / `message` / `requestId`

2. **家属 APP 服务模块**（依赖 §1.3 / §1.7）：
   - `AuthApi` — 登录、Token 刷新、二次身份验证
   - `GuardianshipApi` — 音视频对讲请求/终止、通知偏好更新、手动救援、车窗控制、查询车窗状态、查询家属权限、签发 SparkRTC Token
   - `DriverApi` — 查询驾驶员风险状态

3. **车队大屏服务模块**（依赖 §1.4）：
   - `FleetApi` — 疲劳分布、脱线车辆、轨迹查询、高风险司机钻取、报告生成/下载、绩效预警订阅/取消

4. **WebSocket 客户端**：
   - `GuardianshipWebSocket` — 家属 APP WebSocket 连接管理（心跳、自动重连、离线消息补推）
   - `FleetWebSocket` — 车队大屏 WebSocket 连接管理

## 输出要求

1. 完整类型定义文件，放置于 `code/frontend/model/` 目录下
2. 完整 API 客户端代码，放置于 `code/frontend/api/` 目录下
3. 代码结构清晰、注释完整，符合 TypeScript/ArkTS 编码规范
4. 严格遵循 `docs/ood_interface.md` 中的接口契约定义，不得遗漏字段或枚举值
5. 考虑实际使用场景，提供合理的错误处理和类型安全

## 参考资源

- `docs/ood_interface.md` §4.1 家属 APP 对接接口清单
- `docs/ood_interface.md` §4.2 车队大屏对接
- `docs/ood_interface.md` §1 REST API 契约完整定义
- `docs/ood_interface.md` §3 WebSocket / SparkRTC 集成
- `docs/ood_interface.md` §1.7 Auth 认证服务
