# 质量审查报告（v5 / 第2轮）

> 审查对象：`a_v5_copy_from_v4.md`（API/接口层 OOD 设计 v6）
> 审查依据：需求文档 `requirement.md`、领域层 OOD `docs/ood_domain.md`、应用层 OOD `docs/ood_application.md`
> 审查视角：本报告基于 v1 审查报告（`b_v5_diag_v1.md`）及质询反馈（`b_v5_challenge_v1.md`）修订。在保留 v1 既有问题发现的基础上，增补需求响应充分度评估、消费者视角走查、边界与异常处理审查，以及对 §2/§3/§5 的深层审查。同时纠正 v1 中严重程度判定不一致的问题。

---

## 一、需求响应充分度评估

以下按需求 `requirement.md` 的 5 个设计产出要求及 5 条约束逐项对照产出覆盖情况。

### 1.1 REST API 契约（需求 §1）

需求要求：六个应用服务的全部 REST 端点清单，每个端点覆盖"路径、方法、请求体/查询参数、响应体、HTTP 状态码、认证头"7 个要素。按 OpenAPI 3.0 风格描述。

| 服务 | 端点表列数 | 7要素覆盖 | 问题说明 |
|------|:--:|------|------|
| S1 RiskMonitoringService | 8列（含查询参数） | 完整 ✓ | — |
| S2 InterventionService | 8列（含查询参数） | 完整 ✓ | 但缺失错误响应文档（见问题7） |
| S3 RemoteGuardianshipService | 7列（无查询参数） | 完整 ✓ | S3 无查询参数端点，7列合理 |
| S4 FleetManagementService | 8列（含查询参数） | 基本完整 ⚠ | 缺失部分错误响应码（见问题10） |
| S5 EmergencyRescueService | 7列 | 不完整 ✗ | 缺失"查询参数"列，`queryRescueHistory` 的查询参数仅以散文描述于表外（行551）。见问题7 |
| S6 OTAManagementService | 7列 | 不完整 ✗ | 缺失"查询参数"列，`queryUpgradeProgress`（行614）和 `queryUpgradeHistory/{vehicleId}`（行656）的查询参数仅以散文描述于表外。见问题7 |

**结论**：S5 和 S6 端点表因缺失"查询参数"列而未完整满足需求 §1 的 7 要素要求——调用方需在表外散文描述中查找查询参数，而非从标准化表格中获取，降低了契约的可解析性和工具生成友好度。

### 1.2 MQTT 主题设计（需求 §2）

需求要求："按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）"。

- Topic 路由总表（§2.1）：22 个 Topic 全覆盖，方向/模板/QoS/数据分类/Payload说明 5 维信息完备 ✓
- Payload 定义（§2.2）：核心 Topic 提供完整 JSON Schema（SensorReading、SafetyAlertEvent 等 17 个），次要推送消息提供表格式字段定义（6 个），共 23 个 Payload 定义 ✓
- QoS 等级策略（§2.3）：QoS 0/1 适用场景与选择理由明确 ✓

**结论**：MQTT 主题设计充分响应需求。存在一处次要格式问题：Ack 主题模板使用 `${commandId}`（行702）与其余 `{variable}` 语法不一致（见问题9）。

### 1.3 WebSocket/SparkRTC 集成（需求 §3）

需求要求："家属 APP 音视频对讲、远程视频监控的 WebSocket 信令协议与 SparkRTC 房间管理接口"。

- WebSocket 信令协议（§3.1）：连接建立流程、上行/下行消息表（6 种上行 + 10 种下行）、心跳与重连策略（含离线消息补推与隐私约束）全覆盖 ✓
- SparkRTC 房间管理（§3.2）：房间生命周期流程图、房间参数表、Token 签发、高危失能豁免机制全覆盖 ✓

**结论**：WebSocket/SparkRTC 集成响应充分。

### 1.4 ArkTS 前端对接契约（需求 §4）

需求要求："家属 APP（HarmonyOS）调用的全部后端接口清单与数据模型（DTO）定义，车队大屏的看板数据订阅模型，HMI（车机端）的本地查询接口"。

- 家属 APP 对接（§4.1）：REST API 调用列表（8 项）、ArkTS DTO 定义（含 WebSocket 消息模型）、连接管理示例代码 ✓
  - **遗漏**：REST API 调用列表缺失 `endMediaSession`（DELETE 端点，见问题5）
  - **类型定义不完整**：AlertType 和 RiskLevel 枚举值不全（见问题4）
- 车队大屏对接（§4.2）：WebSocket + REST 混合看板数据订阅模型、TypeScript 消息模型 ✓
- HMI 本地接口（§4.3）：7 项进程内调用接口全覆盖 ✓

**结论**：家属 APP 对接契约存在 2 处遗漏（端点遗漏 + 类型不全），其余部分响应充分。

### 1.5 安全设计（需求 §5）

需求要求："API 认证（JWT/OAuth2）、接口限流策略（令牌桶/漏桶）、MQTT 设备鉴权（X.509 证书或 Token 认证）、敏感数据传输加密策略"。

| 需求项 | 产出位置 | 覆盖 |
|------|------|:--:|
| JWT/OAuth2 认证 | §5.1 - JWT 结构、签发校验流程、角色→权限映射 | ✓ |
| 接口限流 | §5.3 - 令牌桶算法、分角色分端点限流表、429 响应与退避策略 | ✓ |
| MQTT 设备鉴权 | §5.4 - X.509 证书方案（含备选 DeviceSecret）、TLS、证书生命周期 | ✓ |
| 敏感数据加密 | §5.5 - 9 类数据加密策略表、密钥管理（KMS） | ✓ |
| 隐私边界（BR-04） | §5.6 - 8 项安全校验点（含 familyAccountId 横向越权防护） | ✓ |

**结论**：安全设计全面覆盖需求。所有 4 项安全要求及 BR-04 隐私边界均有对应章节。

### 1.6 约束响应度

| 约束 | 响应情况 |
|------|------|
| 不实现具体代码，产出接口契约级设计文档 | ✓ 全文为契约级描述 |
| 所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法 | ⚠ `createRescueReport` 方法未在应用层 OOD 中形式化定义（见问题1） |
| MQTT Topic 设计需与领域事件和感知上报通道对齐 | ✓ 路由表对齐领域事件命名 |
| 安全设计需覆盖 BR-04 隐私边界和认证要求 | ✓ |
| 需考虑边缘—云协同架构的特殊性 | ✓ §1.1 部署边界说明、§4.3 HMI 本地接口体现了边缘 vs 云端区分 |

---

## 二、问题清单

### 问题 1（严重·接口契约缺失 — 多轮迭代未根本解决）

**问题描述**：S3→S5 手动救援流转的核心依赖方法 `IEmergencyRescueService.createRescueReport()` 至今未在应用层 OOD 中形式化定义。

- API OOD §1.3 "S3→S5 手动救援流转说明"（行211）明确 S3 编排步骤 (2) 为"调用 S5 `IEmergencyRescueService.createRescueReport()` 创建救援报告并获取 `rescueReportId`"
- API OOD §1.3 TriggerManualRescueResponse（行200-209）依赖 `rescueReportId` 字段，其来源为上述方法调用
- 但 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表（行397-402）仅含 `confirmSOSReport`、`issueRescueToken`、`verifyRescueToken`、`queryRescueHistory` 四个方法，不存在 `createRescueReport`
- `docs/ood_application.md` §4.5 S5 DTO 中也无对应的 `CreateRescueReportRequest`/`CreateRescueReportResponse` 定义
- 此问题历经 v4（严重标注）、v5（添加"⚠ 接口契约待补"警告注释）两轮迭代，实际阻塞未解除

**所在位置**：
- `a_v5_copy_from_v4.md` §1.3 行207–216
- 对比 `docs/ood_application.md` §3.5 行397–402（缺少方法）、§4.5 行775–824（缺少DTO）

**严重程度**：严重

**改进建议**：在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入 `CreateRescueReportRequest`——至少含 `driverId: DriverId`、`triggerSource: RescueTriggerType`、`initialStatus: RescueRecordStatus`，输出 `CreateRescueReportResponse`——至少含 `rescueReportId: RescueReportId`、`status: RescueRecordStatus`，以及事务属性和异常处理）。API OOD 中"⚠ 接口契约待补"注解应在该方法落地后移除。

---

### 问题 2（中等·跨层 DTO 不一致 — 二次验证凭证字段缺失）

**问题描述**：API OOD 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 均包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段。

- API OOD §1.3 `RequestMediaSessionRequest`（行149–156）含 `secondaryAuthToken: "otp-token-xxx"`
- API OOD §1.3 `TriggerManualRescueRequest`（行189–195）含 `secondaryAuthToken: "otp-token-xxx"`
- 对比 `docs/ood_application.md` §4.3：
  - `RequestMediaSessionRequest`（行576–579）仅含 `familyAccountId`、`driverId`、`sessionType`，**无** `secondaryAuthToken`
  - `TriggerManualRescueRequest`（行595–598）仅含 `familyAccountId`、`driverId`，**无** `secondaryAuthToken`
- 同一应用 OOD §4.3 的 `ControlVehicleWindowRequest`（行606–610）**已包含** `secondaryAuthToken` 字段，证明其余两处为遗漏而非有意省略

实际影响：前端按 API OOD 传入 `secondaryAuthToken`，应用层 DTO 反序列化时将丢失此字段，二次验证凭证无法传入应用服务入口，导致高敏操作的安全门控失效（操作可能被错误拒绝或绕开二次验证检查）。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.3 行154、行193
- 对比 `docs/ood_application.md` §4.3 行576–579、行595–598
- 对比同行606–610（ControlVehicleWindowRequest 已含该字段）

**严重程度**：中等（v1 标注为"一般"；经质询反馈重新审视，`secondaryAuthToken` 的跨层丢失直接影响二次验证门控的正确性，涉及 BR-04 安全约束，属于安全相关缺陷，上调至"中等"）

**改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

---

### 问题 3（中等·跨层 DTO 不一致 — AlertSummary 缺失 gpsLocation 字段）

**问题描述**：API OOD §1.1 `QueryAlertHistoryResponse` 的告警摘要包含 `gpsLocation` 可选字段（行51），但应用层 OOD §4.1 `AlertSummary` DTO（行511–517）不包含此字段。

- API OOD QueryAlertHistoryResponse JSON 示例：`"gpsLocation": { "latitude": 31.2304, "longitude": 121.4737 }`（行51）
- API OOD 说明：`gpsLocation` 为可选字段，与 MQTT SafetyAlertEvent 的 `gps` 字段保持一致（行58）
- 应用层 OOD `AlertSummary`（行511–517）：`alertId`、`alertType`、`riskLevel`、`occurredAt`、`resolvedAt`、`tripId`，**无** `gpsLocation`
- ArkTS `AlertSummary`（行1580–1588）**包含** `gpsLocation?: GeoPoint` 字段，说明前端侧已按 API 契约定义此字段

实际影响：前端按 ArkTS/API OOD 契约期望 `gpsLocation` 字段，但应用层返回的 AlertSummary 不包含此信息，GPS 信息丢失。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.1 行51
- 对比 `docs/ood_application.md` §4.1 行511–517

**严重程度**：中等（v1 标注为"一般"；考虑到 gpsLocation 是告警定位的关键信息，影响家属告警推送和车队管理的地图渲染，上调至"中等"）

**改进建议**：在 `docs/ood_application.md` §4.1 `AlertSummary` DTO 中补充 `gpsLocation: Optional<GeoPoint>` 字段。

---

### 问题 4（中等·ArkTS DTO 类型定义不完整）

**问题描述**：§4.1 ArkTS 数据模型定义中，`AlertType` 和 `RiskLevel` 两个核心枚举类型均无独立类型声明，仅以行内注释隐式定义，且取值不完整：

- `AlertType` 在 `ActiveAlertEntry` 注释（行1462）中仅列出 5 个值 `'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY'`，**遗漏 `PERFORMANCE_WARNING`**——该值出现在 §1.1 查询参数文档（行61）、MQTT SafetyAlertEvent Schema（行763）、以及 MQTT AlertTriggeredEvent（行1211）
- `RiskLevel` 在 `ActiveAlertEntry` 注释（行1463）中仅列出 `'L2_WARNING' | 'L3_CRITICAL'`，**遗漏 `'L1_HINT'`**——该值出现在 §1.1 查询参数文档（行62）、S4 GetFatigueDistributionResponse（行346）、MQTT DriverStatusSnapshot（行802）等多处
- `UpdateNotificationPreferenceReq.preferredRiskLevels`（行1485）使用 `Array<RiskLevel>`，若 `RiskLevel` 不含 `'L1_HINT'`，家属将无法通过类型系统配置接收 L1 级别通知

从使用者视角：ArkTS 开发者需从多处行内注释自行拼凑完整枚举定义，类型检查器无法捕获枚举值使用错误，前后端枚举值不匹配的 bug 在编译期不可发现。

**所在位置**：`a_v5_copy_from_v4.md` §4.1 行1462–1463、行1485

**严重程度**：中等（v1 标注为"一般"；考虑类型安全直接影响前端编译期错误检测能力，上调至"中等"）

**改进建议**：在 §4.1 ArkTS DTO 定义前新增独立的类型声明并统一引用：
```typescript
type AlertType = 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY' | 'PERFORMANCE_WARNING'
type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'
```
移除各接口中 `AlertType` 和 `RiskLevel` 的行内重复注释，统一引用独立类型定义。

---

### 问题 5（轻微·前端对接清单遗漏 endMediaSession 端点）

**问题描述**：§4.1 家属 APP REST API 调用列表遗漏了 `endMediaSession`（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`）端点。此端点在 §1.3 S3 REST 端点表中已定义（行127），对应应用层 `IRemoteGuardianshipService.endMediaSession` 方法（`docs/ood_application.md` 行267）。§4.1 开篇明确声明的双通道策略（行1434）要求两个通道均应在 REST API 调用列表中体现，但 REST 降级通道的结束会话端点被遗漏。

**所在位置**：`a_v5_copy_from_v4.md` §4.1 行1436–1448（REST API 调用列表），对比 §1.3 行127（S3 REST 端点表）

**严重程度**：轻微

**改进建议**：在 §4.1 REST API 调用列表中补充"终止音视频会话"行（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`），响应码 204 无响应体，ArkTS 前端无需专门 DTO 类型。

---

### 问题 6（轻微·文档结构建议 — S5 内部接口可见性不足）

**问题描述**：v1 审查将此问题定位为"结构缺陷"。经质询反馈重新审视：API OOD §1.3 已通过"S3→S5 手动救援流转说明"（行207–216）阐明了 S3 编排调用 S5 的流程，`createRescueReport` 作为内部编排调用不在 REST 端点表中出现属于合理设计选择——API OOD 关注对外契约，内部编排调用归属应用层 OOD 范畴。

然而，从使用者视角（负责 S3 实现的下游开发者），该编排流程引用的 `IEmergencyRescueService.createRescueReport()` 方法需从应用层 OOD 中查阅，但应用层 OOD 中该方法也不存在（见问题1）。使用者需要跨两层文档（API OOD + 应用层 OOD）才能追踪到一个"待补充"的接口。将 v1 审查中的"结构缺陷"改为以下可操作建议：

**改进建议**：在 §1.5 S5 端点表后添加一段说明，明确 S5 供 S3 内部编排调用的方法（待应用层 OOD 补全 `createRescueReport` 后，此处补充交叉引用）。此举使 S5 的功能全貌（对外 REST + 内部编排）在 API OOD 中可见一斑，减少实现者跨文档追踪的认知负担。

**所在位置**：`a_v5_copy_from_v4.md` §1.5 行463–556

**严重程度**：轻微（v1 标注为"轻微"；质询意见认为缺乏客观判定标准——认同，降级为文档可读性改进建议）

---

### 问题 7（中等·S5/S6 端点表缺失"查询参数"列）

**问题描述**：S5 和 S6 的 REST 端点表使用 7 列格式（不含"查询参数"列），但存在需要查询参数的 GET 端点：

- S5 `queryRescueHistory`（行551）支持 `driverId`、`vehicleId`、`startTime`、`endTime`、`page`、`size` 共 6 个查询参数，但端点表（行467–472）为 7 列格式，无"查询参数"列，参数仅以散文描述于表外
- S6 `queryUpgradeProgress`（行614）有必填参数 `vehicleIds`（comma-separated），端点表（行563–569）为 7 列格式
- S6 `queryUpgradeHistory/{vehicleId}`（行656）有 `page`、`size` 参数，同样未在表内体现

对比：S1（行20）、S2（行79，已在 v6 修正）、S4（行327）均使用 8 列格式包含"查询参数"列。S5 和 S6 的 7 列格式与其余服务不一致，调用方需在不同服务的端点表之间切换查询参数的查找方式。

**所在位置**：`a_v5_copy_from_v4.md` §1.5 行467–472、§1.6 行563–569

**严重程度**：中等（影响契约的结构化完整性和工具可解析性）

**改进建议**：将 S5 和 S6 端点表扩展为 8 列格式（补充"查询参数"列），将表外散文描述的查询参数纳入表格对应行。

---

### 问题 8（中等·S2 端点表缺失错误响应文档）

**问题描述**：S1（行66–69）、S3（行314–319）、S4（行449）、S5（行553–555）、S6（行676–678）均有错误响应文档，但 S2 §1.2（行71–113）**完全缺失**错误响应。S2 的两个 REST 端点（`queryInterventionStatus`、`queryInterventionHistory`）是云端可访问的 API，调用方（车队大屏、HMI）需要知道可能的错误状态码和处理方式。例如：
- 非法行程 ID 格式 → 应返回 400
- 行程不存在 → 按应用层 OOD 约定返回空集合（非错误），但 API OOD 应明确此行为约定

**所在位置**：`a_v5_copy_from_v4.md` §1.2 行71–113（缺失错误响应小节）

**严重程度**：中等（该节整体缺失错误文档，影响调用方的异常处理逻辑实现）

**改进建议**：在 §1.2 末尾补充错误响应小节，至少包含：无效参数（400）、行程不存在时返回空集合而非 404（与 S1 风格统一）、服务不可用（503）。

---

### 问题 9（轻微·MQTT 主题模板语法不一致）

**问题描述**：§2.1 主题路由总表中，指令执行确认 Ack 主题（行702）使用 `${commandId}` 语法，而其余所有模板变量均使用 `{variable}` 语法（如 `{deviceId}`、`{sensorType}`、`{accountId}` 等）。Payload Schema 小节标题（行961）对同一主题使用 `cmd/{commandId}/ack`（无 `$` 前缀），文档内部不一致。

此问题与 v3 迭代历史问题 7（`${sensorType}` → `{sensorType}` 已修正）同类但被遗漏。

**所在位置**：
- `a_v5_copy_from_v4.md` §2.1 行702（`${commandId}`）
- 对比 §2.2 行961（`{commandId}`，无 `$`）

**严重程度**：轻微

**改进建议**：将行702的 `${commandId}` 统一为 `{commandId}`。

---

### 问题 10（轻微·S4 错误响应码覆盖不完整）

**问题描述**：S4 §1.4 仅针对报告生成端点列出 `504 ReportGenerationTimeout`（行449），但未覆盖以下常见错误场景：
- 无效请求参数（如非法 fleetId、driverId 格式）→ 应有 400
- fleetId/driverId 对应资源不存在 → 应有 404
- 未授权/未认证访问 → 应有 401

对比 S1 和 S3 的错误响应文档覆盖了这些基本类别。S4 作为面向 MANAGER 角色的公共 API，错误码覆盖应与其他服务一致。

**所在位置**：`a_v5_copy_from_v4.md` §1.4 行323–460

**严重程度**：轻微

**改进建议**：在 §1.4 末尾补充错误响应小节，至少包含：400（参数无效）、401（未经认证）、404（资源不存在）、504（报告生成超时，已有）。

---

### 问题 11（一般·跨层 DTO 不一致 — QueryTrajectoryResponse 缺失 dataConsistency 字段）

**问题描述**：API OOD §1.4 `QueryTrajectoryResponse` JSON 示例（行385）包含 `dataConsistency` 字段（枚举 `CONSISTENT | INCONSISTENT`），并说明"当 vehicleId 和 driverId 同时提供但不匹配时，返回 INCONSISTENT"。但应用层 OOD §4.4 `QueryTrajectoryResponse` DTO（行765–768）仅定义 `trajectoryPoints` 和 `totalCount` 两个字段，**不包含** `dataConsistency`。

注意：应用层 OOD §4.4 在 `QueryTrajectoryRequest` 的参数交叉校验说明（行763）中已引用 `dataConsistency = INCONSISTENT` 概念，表明应用层设计者也认可此字段的存在意义，但未将其纳入 DTO 定义，属于遗漏。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.4 行385
- 对比 `docs/ood_application.md` §4.4 行765–768

**严重程度**：一般

**改进建议**：在 `docs/ood_application.md` §4.4 `QueryTrajectoryResponse` 中补充 `dataConsistency: DataConsistency` 字段，并定义 `DataConsistency` 枚举（`CONSISTENT | INCONSISTENT`）。

---

## 三、消费者视角走查

以三个典型集成角色的完整调用链路评估产出可用性。

### 3.1 家属 APP 前端开发者

| 步骤 | 所需信息 | 产出覆盖 | 缺口 |
|------|------|:--:|------|
| 1. 建立 WebSocket 连接 | 连接端点、认证方式、心跳参数 | §3.1 ✓ | — |
| 2. 订阅驾驶员状态 | 上行消息格式、响应确认格式 | §3.1 行1307 ✓ | — |
| 3. 处理告警推送 | 下行 `alert_triggered` 消息模型 | §3.1 + §4.1 AlertSummary ✓ | — |
| 4. 请求建立音视频对讲 | REST 端点、请求/响应 DTO、WebSocket 替代通道 | §1.3 + §4.1 ✓ | — |
| 5. 结束音视频会话 | REST 端点 | §1.3 REST 端点表 ✓ | §4.1 REST 列表遗漏（见问题5） |
| 6. SparkRTC Token 获取与续期 | Token 签发端点、续期下行消息 | §1.3 + §3.1 `token_renewed` ✓ | — |
| 7. 查询监护权限 | REST 端点、响应 DTO | §1.3 + §4.1 ✓ | — |
| 8. 触发手动救援 | REST 端点、响应 DTO（含 rescueReportId） | §1.3 ✓ | — |
| 9. WebSocket 断线重连 | 重连退避策略、离线消息补推、订阅恢复 | §3.1 ✓ | — |
| 10. 类型系统完整性 | AlertType/RiskLevel 完整枚举 | §4.1 | 类型定义不全（见问题4） |

**走查结论**：家属 APP 开发者可独立完成大部分集成工作，但需自行补全枚举类型定义，且 REST 降级通道缺少结束会话端点。

### 3.2 救援机构集成者

| 步骤 | 所需信息 | 产出覆盖 | 缺口 |
|------|------|:--:|------|
| 1. 认证获取 RESCUE JWT | 认证流程、角色映射 | §5.1 ✓ | — |
| 2. 消费 SOS 报告 | S5 REST 端点、请求/响应 DTO | §1.5 ✓ | — |
| 3. 签发救援授权凭证 | 凭证结构、有效期、操作集合 | §1.5 + 应用层 DTO ✓ | — |
| 4. 消费救援凭证执行操作 | 校验逻辑、并发冲突处理 | §1.5 ✓ | — |
| 5. 查询救援历史 | REST 端点、查询参数、响应 DTO | §1.5（参数在表外）⚠ | 查询参数不在表内（见问题7） |

**走查结论**：救援机构集成路径基本完整，但查询参数不在标准化表格中增加查阅成本。

### 3.3 车队运维管理员

| 步骤 | 所需信息 | 产出覆盖 | 缺口 |
|------|------|:--:|------|
| 1. 查看看板数据 | 看板 REST 端点 + WebSocket 推送 | §1.4 + §4.2 ✓ | — |
| 2. 钻取高风险司机 | 下钻端点、分页参数 | §1.4 ✓ | — |
| 3. 生成/下载报告 | 报告生成/下载端点、格式参数 | §1.4 ✓ | — |
| 4. 管理 OTA 升级 | 升级任务创建/查询/回滚/取消 | §1.6 | 查询参数不在表内（见问题7） |
| 5. 处理错误响应 | 各端点错误码 | §1.4 | 部分缺失（见问题10） |

**走查结论**：车队运维路径基本完整，OTA 升级管理端点的查询参数需从表外散文获取。

---

## 四、边界与异常处理评估

### 4.1 错误响应覆盖度

| 服务 | 400 | 401 | 403 | 404 | 409 | 429 | 503 | 504 | 覆盖度 |
|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| S1 | ✓ | — | — | ✓ | — | — | ✓ | — | 基本 |
| S2 | — | — | — | — | — | — | — | — | 缺失（见问题8） |
| S3 | — | ✓ | ✓ | — | ✓ | — | ✓ | ✓ | 完整 |
| S4 | — | — | — | — | — | — | — | ✓ | 不完整（见问题10） |
| S5 | — | — | ✓ | — | ✓ | — | — | — | 基本 |
| S6 | ✓ | — | — | — | ✓ | — | — | — | 基本 |

- S2 完全缺失错误响应文档
- S4 仅覆盖 504 超时场景
- 401（未认证）和 404（资源不存在）作为基础 HTTP 状态码在多处缺失，虽可由 API 网关统一处理，但在契约文档中明确可提高调用方的确定性

### 4.2 并发与竞态场景

| 场景 | API OOD 覆盖 | 说明 |
|------|:--:|------|
| 多个家属并发触发手动救援 | 未覆盖 | API OOD 未描述；应用层 OOD §9.2 描述"两类救援路径独立、不互斥"——可引用 |
| 同一 RescueToken 并发消费 | ✓ | §1.5 VerifyRescueTokenResponse + 错误响应 409 ConcurrentConsumption（行555） |
| 家属同时请求对讲与权限撤销 | 未覆盖 | API OOD 未描述；应用层 OOD §9.2 有 SystemAccount 乐观锁策略——建议在 API OOD 中引用 |
| 多管理员并发刷新看板 | 未覆盖 | 不影响数据一致性（只读），但无文档说明 |

**评估**：API OOD 仅直接覆盖了 RescueToken 并发消费场景（含明确错误码 409），其余并发场景由应用层 OOD 覆盖但未在 API OOD 中引用。从独立使用 API OOD 的接口层开发者视角，并发行为约定不够透明。

### 4.3 降级路径

| 降级场景 | 覆盖 |
|------|:--:|
| WebSocket 断开 → 重连恢复 | ✓ §3.1 心跳与重连策略（明确退避算法、最多 5 次、订阅恢复、告警补推） |
| WebSocket 不可用 → REST 降级（对讲建立） | ✓ §4.1 双通道策略说明（行1434） |
| REST 降级（结束会话） | ✗ §4.1 REST 列表缺失 endMediaSession（见问题5） |
| MQTT 设备断连 → 重连 | 未在 API OOD 中覆盖（属 IoTDA 基础设施层范围，但契约文档可引用） |
| SparkRTC Token 过期后音视频持续 | ✓ §3.2 行1424 说明"SDK 不会立即中断已建立音视频流" |
| SOS 投递失败降级 | 在应用层 OOD §3.5 中详细定义（SOS 重试策略），API OOD 未引用 |

**评估**：WebSocket 断开重连和 SparkRTC Token 续期的降级路径设计充分。但家属 APP 的 REST 降级通道不完整（缺失 endMediaSession），SOS 投递失败降级策略仅在应用层 OOD 中可见，API OOD 使用者需跨文档查阅。

---

## 五、总体评价

### 5.1 质量总结

产出整体框架完整，覆盖需求要求的五个部分。前序 6 轮迭代反馈（v1–v6 修订说明）的大多数问题已修复。本报告在本轮产出中识别出 **11 个问题**：

- **严重**：1 个（`createRescueReport` 接口契约缺失——持续多轮未根本解决，S3 实现者无法按 API OOD 描述的编排逻辑完成开发）
- **中等**：5 个（`secondaryAuthToken` 跨层丢失、`gpsLocation` 跨层缺失、ArkTS 类型定义不全、S5/S6 端点表缺查询参数列、S2 缺错误响应）
- **一般**：1 个（`dataConsistency` 跨层缺失）
- **轻微**：4 个（endMediaSession 端点遗漏、S5 内部接口可见性建议、MQTT 语法不一致、S4 错误码不完整）

### 5.2 可直接使用性判断

从使用者视角，当前产出**不可直接投入使用**——1 个严重问题（`createRescueReport` 契约缺失）阻塞 S3 开发者完成与 S5 的集成编排；3 个跨层 DTO 不一致问题（问题 2、3、11）导致前端传入的字段在应用层丢失，影响功能正确性和安全门控。

### 5.3 优先修复建议

1. **必须修复（P0）**：问题 1 —— 在应用层 OOD 中补充 `createRescueReport` 接口契约及 DTO
2. **应修复（P1）**：问题 2、3、11 —— 修复三处跨层 DTO 字段不一致，确保前后端数据类型一致
3. **建议修复（P2）**：问题 4 —— 补全 ArkTS 枚举类型定义，提升前端类型安全
4. **可选修复（P3）**：问题 5–10 —— 文档完整性和一致性改进

---

## 修订说明（v2）

以下为对本报告 v1（`b_v5_diag_v1.md`）收到质询反馈（`b_v5_challenge_v1.md`）后的修订回应：

| 质询意见 | 回应 |
|---------|------|
| **审查报告定位为缺陷发现工具而非系统性质量评估工具**：未覆盖需求响应充分度、整体深度和完整性、边界与异常处理的完备性 | **采纳**。新增第一章"需求响应充分度评估"（按 5 个设计产出要求 + 5 条约束逐项对照）、第三章"消费者视角走查"（三家 APP 开发者、救援机构集成者、车队运维三个角色分别走查）、第四章"边界与异常处理评估"（错误响应覆盖度表、并发场景表、降级路径表）。增补 MQTT/WebSocket/安全章节深层审查发现的 5 个新问题（问题 7–11）。 |
| **问题 6 缺乏客观判定标准**：API OOD §1.5 的 S5 端点表列出的是对外暴露的 REST 端点，`createRescueReport` 不在其中并非错误——已在 §1.3 阐明编排关系 | **部分采纳**。问题 6 降级为文档可读性改进建议（"文档结构建议"），从"结构缺陷"改为更具体的可操作建议：在 S5 端点表后添加内部编排方法引用说明块。同时注明用户若认为此建议不必要可忽略。 |
| **严重程度判定不一致**：总体评价将 `secondaryAuthToken` 归为"两个严重问题之一"，但问题清单标注为"一般" | **采纳并修正**。问题 2（`secondaryAuthToken` 跨层不一致）从"一般"上调至"中等"——该字段丢失直接影响二次验证门控的正确性，属于安全相关缺陷，恢复其合理严重程度。问题 3（`gpsLocation`）同步从"一般"上调至"中等"——告警 GPS 信息丢失影响家属推送和车队地图渲染。总体评价段相应修正为"1 个严重问题 + 5 个中等问题"，消除内部矛盾。 |
| **§2/§3/§5 章节零发现**：并不意味着这些章节完美，而是未深入审查 | **采纳**。对 MQTT 路由表逐行走查（发现 `${commandId}` 语法不一致——问题 9）、对 WebSocket 协议逐消息类型校验（确认完整，无额外问题）、对安全设计按需求 §5 的 4 个细项逐项对照（确认全面覆盖）。 |
| **缺失按需求分项的对照检查** | **采纳**。新增 §1.1–§1.6 逐项对照表，明确标注每项需求细项的覆盖状态和具体缺口位置。 |
| **缺失消费者视角走查** | **采纳**。新增第三章，从三个消费者角色分别走查完整调用链路。 |
| **缺失边界与异常处理审查** | **采纳**。新增第四章，含错误响应覆盖度矩阵、并发竞态场景表、降级路径表。 |
| **缺失整体深度评估** | **采纳**。总体评价中补充"可直接使用性判断"和"优先修复建议"，从消费者视角给出明确的可用性结论和修复优先级。 |
