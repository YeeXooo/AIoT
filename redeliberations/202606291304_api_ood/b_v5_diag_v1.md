# 质量审查报告（v5 / 首轮）

> 审查对象：`a_v5_copy_from_v4.md`（API/接口层 OOD 设计 v6）
> 审查依据：需求文档 `requirement.md`、领域层 OOD `docs/ood_domain.md`、应用层 OOD `docs/ood_application.md`
> 审查视角：内部审议已覆盖技术可行性，本审查侧重**需求响应充分度、产出可用性、整体深度与完整性**

---

## 问题清单

### 问题 1（严重·接口契约缺失 — 持续未解决）

**问题描述**：S3→S5 手动救援流转的核心依赖方法 `IEmergencyRescueService.createRescueReport()` 至今未在应用层 OOD 中形式化定义。该 API OOD §1.3 在 TriggerManualRescueResponse 及"S3→S5 手动救援流转说明"中多处引用此方法作为 S3 编排 S5 的关键步骤（创建救援报告、获取 `rescueReportId`），但 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中不存在此方法，§4.5 S5 DTO 中也无对应的 `CreateRescueReportRequest`/`CreateRescueReportResponse` 定义。API OOD 以"⚠ 接口契约待补"注解标注此问题，但未解决。

此问题历经 v4（严重）、v5（添加警告注解放到本次 v6 产出）两轮迭代，实际阻塞未解除。从使用者视角，S3 实现者无法按 API OOD 描述的编排逻辑完成开发——该方法不存在契约，无法调用。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.3 第 207–216 行（TriggerManualRescueResponse 字段说明、S3→S5 手动救援流转说明、"⚠ 接口契约待补"注解）
- 对比 `docs/ood_application.md` §3.5 第 396–403 行（`IEmergencyRescueService` 接口方法表，缺少 `createRescueReport`）
- 对比 `docs/ood_application.md` §4.5 第 775–824 行（S5 DTO，缺少 `CreateRescueReportRequest`/`CreateRescueReportResponse`）

**严重程度**：严重

**改进建议**：在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入 DTO、输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 `CreateRescueReportRequest`（至少含 `driverId`、`triggerSource`、`initialStatus`）和 `CreateRescueReportResponse`（至少含 `rescueReportId`、`status`）DTO 定义。API OOD 中对应"⚠ 接口契约待补"注解应在该方法落地后移除。

---

### 问题 2（一般·跨层 DTO 不一致 — 持续未解决）

**问题描述**：API OOD 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 均包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段。具体：
- API OOD §1.3 `RequestMediaSessionRequest`（行 149–156）含 `secondaryAuthToken`，对应 `docs/ood_application.md` §4.3（行 576–579）无此字段。
- API OOD §1.3 `TriggerManualRescueRequest`（行 189–195）含 `secondaryAuthToken`，对应 `docs/ood_application.md` §4.3（行 595–598）无此字段。

注意：同一应用 OOD 的 `ControlVehicleWindowRequest`（行 610）**已**包含 `secondaryAuthToken`，说明这不是有意省略而是一个遗漏。API OOD v6 修订将此标注为"保留（无需修改本文件）"，但应用层 OOD 的遗漏尚未修复。从使用者视角，前端按 API OOD 传入 `secondaryAuthToken` 的应用层 DTO 反序列化将丢失此字段，导致二次验证失效。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.3 行 154、行 193
- 对比 `docs/ood_application.md` §4.3 行 576–579、行 595–598
- 对比 `docs/ood_application.md` §4.3 行 610（ControlVehicleWindowRequest 已含此字段，证明其余两处为遗漏）

**严重程度**：一般

**改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

---

### 问题 3（一般·跨层 DTO 不一致 — 字段遗漏）

**问题描述**：API OOD §1.1 `QueryAlertHistoryResponse` 的告警摘要中包含 `gpsLocation` 可选字段（行 51），但应用层 OOD §4.1 `AlertSummary` DTO（行 511–517）中不包含此字段。前端按 API OOD 契约开发后，应用层返回的数据中 GPS 信息将丢失。此问题与问题 2 性质相同——API OOD 的接口契约比应用层 DTO 更完整，存在字段级不一致。

**所在位置**：
- `a_v5_copy_from_v4.md` §1.1 行 51（`gpsLocation` 字段）
- 对比 `docs/ood_application.md` §4.1 行 511–517（`AlertSummary` DTO）

**严重程度**：一般

**改进建议**：在 `docs/ood_application.md` §4.1 `AlertSummary` DTO 中补充 `gpsLocation: Optional<GeoPoint>` 字段。

---

### 问题 4（一般·ArkTS DTO 类型定义不完整）

**问题描述**：§4.1 ArkTS 数据模型定义中，`AlertType` 和 `RiskLevel` 两个核心枚举类型均无独立类型声明，仅以行内注释隐式定义，且取值不完整：
- `AlertType` 在 `ActiveAlertEntry` 注释（行 1462）中列出 5 个值，遗漏 `PERFORMANCE_WARNING`——该值出现在 §1.1 查询参数文档（行 61）和 MQTT SafetyAlertEvent Schema 中。
- `RiskLevel` 在 `ActiveAlertEntry` 注释（行 1463）中仅列出 `'L2_WARNING' | 'L3_CRITICAL'`，遗漏 `'L1_HINT'`——该值出现在 §1.1 查询参数文档（行 62）和领域层 OOD `VO-01 RiskLevel` 枚举中。
- `UpdateNotificationPreferenceReq.preferredRiskLevels` 使用 `Array<RiskLevel>` 类型（行 1485），若 `RiskLevel` 不含 `'L1_HINT'`，家属将无法配置接收 L1 级别通知（尽管 L1 本期无触发路径，但类型定义应反映完整枚举）。

从使用者视角，ArkTS 开发者需自行推断完整类型定义，易导致前后端枚举值不匹配。

**所在位置**：`a_v5_copy_from_v4.md` §4.1 行 1462–1463、行 1485

**严重程度**：一般

**改进建议**：在 §4.1 ArkTS DTO 定义前新增独立的类型声明：
```typescript
type AlertType = 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY' | 'PERFORMANCE_WARNING'
type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'
```
并移除各接口中 `AlertType` 和 `RiskLevel` 的行内重复注释，统一引用此独立类型定义。

---

### 问题 5（轻微·前端对接清单遗漏）

**问题描述**：§4.1 家属 APP REST API 调用列表遗漏了 `endMediaSession`（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`）端点。此端点在 §1.3 S3 REST 端点表中已定义（行 127），对应应用层 `IRemoteGuardianshipService.endMediaSession` 方法。虽然家属 APP 可通过 WebSocket `end_media` 消息结束会话（§3.1 行 1311），但当 WebSocket 不可用时 REST 是降级通道，§4.1 开篇明确声明的双通道策略（行 1434）要求两个通道均应在 REST API 调用列表中体现。

**所在位置**：`a_v5_copy_from_v4.md` §4.1 行 1436–1448（REST API 调用列表），对比 §1.3 行 127（S3 REST 端点表）

**严重程度**：轻微

**改进建议**：在 §4.1 REST API 调用列表中补充"终止音视频会话"行（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`），响应码 204 无响应体，ArkTS 前端无需专门 DTO 类型。

---

### 问题 6（轻微·结构缺陷 — S5 救援报告创建入口不可见）

**问题描述**：S5 EmergencyRescueService 的 REST 端点表（§1.5）仅包含 4 个端点：SOS 确认、签发授权凭证、校验凭证、查询救援历史。S3 手动救援触发内部需调用 S5 创建救援报告，但此操作在 S5 端点表中不可见——既无外部 REST 端点，也无内部方法引用说明。从使用者视角，S5 的职责边界中缺失"救援报告创建"这一内部接口，只能从 S3 的编排说明中推断 S5 应存在此能力。

**所在位置**：`a_v5_copy_from_v4.md` §1.5 行 463–556（S5 端点表），对比 §1.3 行 207–216（S3→S5 流转说明）

**严重程度**：轻微

**改进建议**：在 §1.5 S5 端点表前或后添加一段说明，明确 S5 的内部接口（非 REST 暴露）：`createRescueReport` 供 S3 编排调用，在应用层 OOD 中定义后在此处引用。此举使 S5 的功能全貌可见，避免依赖方仅从 S3 文档推断 S5 能力。

---

## 总体评价

产出的整体框架完整，覆盖了需求要求的五个部分（REST API、MQTT、WebSocket/SparkRTC、ArkTS、安全设计），前序 5 轮迭代反馈的大多数问题已修复。两个严重问题（`createRescueReport` 接口缺失、`secondaryAuthToken` 跨层不一致）经多轮迭代尚未根本解决，当前产出对使用者不可直接投入使用——S3 实现者无法完成与 S5 的编排集成，前端传入的二次验证令牌在应用层会丢失。

其余四个问题（`gpsLocation` 字段遗漏、ArkTS 类型定义不完整、`endMediaSession` 端点在 §4.1 遗漏、S5 内部接口不可见）为轻度至中度缺陷，修复成本低但影响下游开发效率。
