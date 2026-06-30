# API/接口层 OOD 设计方案 质量审查报告（b_v1 / v1）

> 审查对象：`a_v1_output_v1.md`
> 审查依据：`requirement.md`（用户需求）、`docs/ood_application.md`（应用层 OOD）、`docs/ood_domain.md`（领域层 OOD）、`docs/requirements.md`（需求文档）
> 审查视角：需求响应充分度、整体深度和完整性、事实错误/逻辑矛盾（不重复验证内部审议已覆盖的技术可行性）

---

## 审查结论

产出整体质量良好：五部分（REST API / MQTT / WebSocket-SparkRTC / ArkTS / 安全）均有覆盖，API 端点与应用层方法映射完整且准确，MQTT 主题路由与领域事件对齐良好，ArkTS 前端 DTO 定义详尽。共发现 6 个质量问题（0 严重、2 中等、4 轻微）。

---

## 问题明细

### 问题 1（中等严重）— S3 缺失家属权限查询 REST 端点

- **问题描述**：需求 `requirement.md:23` 明确要求 S3 RemoteGuardianshipService 覆盖"家属权限查询/管理"。产出 §1.3 的 REST 端点表中无独立的权限查询端点（如 `GET /api/v1/guardianship/permissions/{driverId}`）。当前设计中，家属权限状态仅能通过 WebSocket 的 `access_granted` / `access_revoked` 推送消息被动获知，缺乏主动查询当前监护权限状态（是否持有、权限范围、有效期）的 REST 能力。
- **所在位置**：§1.3 REST 端点表（行 117-125）
- **影响**：家属 APP 在发起高敏操作前无法主动校核自身权限，只能依赖推送消息的被动通知或操作失败的异常响应，降低了用户体验和交互效率。
- **改进建议**：在 S3 REST 端点表中补充 `GET /api/v1/guardianship/{driverId}/permissions` 端点，返回家属对指定驾驶员的当前授权状态（是否已授权、授权范围、授予时间、有效期），映射到应用层 IRemoteGuardianshipService 需新增相应查询方法（当前接口无此方法，需与应用层协调同步新增）。

### 问题 2（中等严重）— MQTT Payload JSON Schema 覆盖不完整

- **问题描述**：需求 `requirement.md:32` 要求"按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）"。产出 §2.1 定义了约 20 个 Topic 路由，但 §2.2 仅提供 4 个核心 Payload 的 JSON Schema（SensorReading、SafetyAlertEvent、DriverStatusSnapshot、干预指令下发）。以下关键 Topic 的 Payload 格式缺失：
  - 车窗控制指令（`cmd/window/down`）
  - 车门解锁指令（`cmd/door/unlock/down`）
  - OTA 升级包下发（`cmd/ota/down`）
  - OTA 回滚指令（`cmd/ota/rollback/down`）
  - 指令 Ack（`cmd/{commandId}/ack`）
  - 传感器故障事件（`sensor/fault/up`）
  - 摄像头遮挡事件（`sensor/occlusion/up`）
  - 驾驶员覆盖信号（`driver/override/up`）
  - 行程评分（`trip/score/up`）
  - 心跳消息（`status/heartbeat/up`）
  - 家属告警推送（`family/{accountId}/alert/push`）
  - 家属权限授予/撤销推送（`family/{accountId}/access/granted`、`family/{accountId}/access/revoked`）
  - 车队告警/绩效推送（`fleet/*`、`app/*`）
- **所在位置**：§2.2（行 595-747）
- **影响**：接口层消费方（设备端、APP 端、大屏端）无法从本设计文档获取完整的 Payload 格式契约，需另行查阅其他文档或推断，增加了集成成本。
- **改进建议**：为 §2.1 路由表中每个 Topic 提供至少字段级的 Payload 格式定义（可简化为表格形式，如"字段名 | 类型 | 说明"，不必全部提供完整 JSON Schema；核心 Topic 提供完整 Schema，次要用表格）。至少补充：车窗控制指令、OTA 指令、指令 Ack、传感器故障、心跳、各推送消息的 Payload 结构。

### 问题 3（轻微）— SparkRTC Token 端点未归属到应用服务

- **问题描述**：产出 §3.2 定义了 `POST /api/v1/sparkrtc/token` 端点（行 864-878），但该端点既未出现在 §1.3 S3 REST 端点表，也未出现在任何其他服务的端点表中。该 Token 签发属于音视频对讲管理功能，应映射到 S3 RemoteGuardianshipService。
- **所在位置**：§3.2（行 864-878）
- **影响**：API 消费者不清楚该端点的归属服务和认证要求，端点清单不完整。
- **改进建议**：将 SparkRTC Token 签发端点纳入 §1.3 S3 REST 端点表，明确其归属 S3 RemoteGuardianshipService，并标注对应的应用层方法（当前应用层 OOD 中无明确对应方法，需同步补充或映射到 `requestMediaSession` 流程中说明）。

### 问题 4（轻微）— QueryAlertHistoryResponse 缺失 GPS 字段

- **问题描述**：产出 §2.2 SafetyAlertEvent JSON Schema（行 631-665）明确包含 `gps: { latitude, longitude }` 字段，且领域 OOD 中 SafetyAlertEvent 实体标注携带 GPS 位置信息。但 §1.1 QueryAlertHistoryResponse 的示例 JSON（行 39-52）仅包含 `alertId`、`alertType`、`riskLevel`、`occurredAt`、`resolvedAt`、`tripId`，缺失 GPS 坐标。历史告警的 GPS 数据是看板热力图和轨迹分析的重要数据来源。
- **所在位置**：§1.1 QueryAlertHistoryResponse（行 39-52）
- **影响**：车队管理员查询历史告警时无法获取告警发生时的 GPS 位置，看板热力图和告警地理分布分析缺少直接数据源，需额外调用轨迹查询接口。
- **改进建议**：在 QueryAlertHistoryResponse 的 AlertSummary 中补充 `gpsLocation` 可选字段（含 latitude、longitude），与 MQTT SafetyAlertEvent 的 gps 字段保持一致。

### 问题 5（轻微）— OTA 升级管理缺少取消未启动任务的 REST 端点

- **问题描述**：需求 `requirement.md:28` 要求 S6 覆盖"升级任务创建/查询"。产出 §1.6 包含创建、查询进度、回滚、查询历史四个端点。但对于已创建但尚未下发（PENDING 状态）的升级任务，缺少取消操作的端点。回滚仅适用于已开始升级的任务。
- **所在位置**：§1.6 全局（行 461-558）
- **影响**：运维人员在创建任务后发现版本错误或窗口不合适时，无法取消未启动的任务，只能等待其执行后再回滚，造成不必要的操作开销。
- **改进建议**：在 S6 REST 端点中补充 `DELETE /api/v1/ota/upgrade-tasks/{taskId}` 端点，允许取消 PENDING 或 TRANSMITTING 阶段的升级任务（终态任务拒绝取消）。对应的应用层 IOTAManagementService 也需新增 `cancelUpgradeTask` 方法。

### 问题 6（轻微）— 安全隐私校验点表缺少家属权限查询的隐私保护规则

- **问题描述**：产出 §5.6 隐私边界安全校验点表（行 1232-1239）覆盖了 DMS 原始图像、家属查询位置、路怒语音存证、管理员查询告警、音视频流留存、驾驶员注销六项。但缺失对"家属主动查询自身监护权限"的隐私校验规则——家属 APP 查询权限时，应校验查询者是否为监护人本人（不能查询他人对自己亲属的监护权限）。
- **所在位置**：§5.6（行 1232-1239）
- **影响**：若未来补充权限查询端点（见问题 1），缺少对应隐私校验规则可能导致权限信息泄露风险。
- **改进建议**：在 §5.6 表中新增一行：`家属查询监护权限 | S3 权限查询入口 | 仅返回与请求方 accountId 关联的监护权限；拒绝查询非本人持有的权限关系`。此建议与问题 1 联动。

---

## 整体质量评价

- **需求响应充分度**：覆盖了需求要求的全部五个设计部分，API 端点与应用层方法映射完整。S3 家属权限查询端点缺失是唯一的需求覆盖缺口。
- **事实准确性**：经与领域层 OOD、应用层 OOD、需求文档交叉比对，未发现事实错误或逻辑矛盾。API 路径、HTTP 方法、请求/响应体的取值枚举与参考文档一致。
- **深度与完整性**：REST API 契约、WebSocket 信令协议、ArkTS 前端 DTO、安全设计均达到契约级设计深度，可供接口层开发直接使用。MQTT JSON Schema 覆盖率和 OTA 端点完备性有提升空间。
- **可行动性**：产出文档层次清晰，类型定义完整，枚举值闭合，接口层开发人员可直接据此编写调用代码和测试用例。

---

## 附录：与参考文档的交叉校验记录

| 校验项 | 产出内容 | 参考依据 | 结论 |
|--------|---------|---------|:--:|
| S1 端点 getDriverRiskStatus | GET `/api/v1/drivers/{driverId}/risk-status` | 应用层 IRiskMonitoringService.getDriverRiskStatus | 一致 |
| S1 端点 queryAlertHistory | GET `/api/v1/drivers/{driverId}/alerts` | 应用层 IRiskMonitoringService.queryAlertHistory | 一致 |
| S2 端点 queryInterventionStatus | GET `/api/v1/trips/{tripId}/interventions/active` | 应用层 IInterventionService.queryInterventionStatus | 一致 |
| S2 端点 queryInterventionHistory | GET `/api/v1/trips/{tripId}/interventions/history` | 应用层 IInterventionService.queryInterventionHistory | 一致 |
| S3 端点 requestMediaSession | POST `/api/v1/guardianship/media-session` | 应用层 IRemoteGuardianshipService.requestMediaSession | 一致 |
| S3 端点 controlVehicleWindow | POST `/api/v1/guardianship/window-control` | 应用层 IRemoteGuardianshipService.controlVehicleWindow | 一致 |
| S3 端点 triggerManualRescue | POST `/api/v1/guardianship/manual-rescue` | 应用层 IRemoteGuardianshipService.triggerManualRescue | 一致 |
| S4 端点 generateReport | POST `/api/v1/fleet/reports` | 应用层 IFleetManagementService.generateReport | 一致 |
| S4 端点 drillDownHighRisk | GET `/api/v1/fleet/{fleetId}/high-risk-drivers` | 应用层 IFleetManagementService.drillDownHighRisk | 一致 |
| S5 端点 confirmSOSReport | POST `/api/v1/emergency/sos-confirm` | 应用层 IEmergencyRescueService.confirmSOSReport | 一致 |
| S6 端点 createUpgradeTask | POST `/api/v1/ota/upgrade-tasks` | 应用层 IOTAManagementService.createUpgradeTask | 一致 |
| StatusColor 取值 | GREEN / YELLOW / RED | 领域层 VO-15：GREEN / YELLOW / RED | 一致 |
| derivedStatusColor 映射 | L1→GREEN, L2→YELLOW, L3→RED | 领域层 VO-15：无风险/L1→绿, L2→黄, L3→红 | 一致 |
| AlertType 枚举值 | 6 个值含 PERFORMANCE_WARNING | 领域层 VO-02：6 个值 | 一致 |
| InterventionType 枚举值 | 10 个值 | 领域层 VO-12 InterventionInstructionType：10 个值 | 一致 |
| MQTT Topic 与领域事件对齐 | 参见路由表 | 领域 OOD §3.5 领域事件表 | 对齐 |
| 报告生成 ≤15s SLA | 明确标注 | 需求 3.5、应用层 §3.4 | 一致 |
| 看板 5min 刷新 | 明确标注 | 需求 3.5 | 一致 |
| BR-04 隐私边界 | §5.6 列出 6 项校验点 | 需求 BR-04 | 覆盖 |
| 家属端自动激活接入 | §3.1 access_granted reason=EMERGENCY_ACTIVATION | 需求 3.4、BR-06、BR-07 | 一致 |
| 驾驶员物理遮挡权 | §3.2 SparkRTC 参数中声明，§4.3 HMI 中列出 | 需求 3.4 | 一致 |
