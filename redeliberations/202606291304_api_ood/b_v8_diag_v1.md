# API/接口层 OOD 设计方案 质量审查报告（v8 / 第 8 轮）

## 审查概况

本轮从使用者视角出发，重点审查：需求响应充分度、事实错误/逻辑矛盾、深度与完整性。内部审议已覆盖的技术可行性等维度不再重复验证。

整体评价：产出已相当完备，五大部分均覆盖，多数前序轮次问题已修正。但仍存在新增的跨层一致性问题、设计死值、版本标识混乱，以及多轮未解决的持续性问题。

---

## 问题清单

### 问题 1（严重 · 跨层 DTO 不一致 · 新增）

**描述**：
API OOD §1.3 的 `ControlVehicleWindowRequest` 包含 `windowPosition` 字段（取值 `FRONT_LEFT` / `FRONT_RIGHT` / `REAR_LEFT` / `REAR_RIGHT`），ArkTS DTO §4.1 的 `ControlVehicleWindowReq` 同样包含该字段。但应用层 OOD `docs/ood_application.md` §4.3 的同名 DTO 仅定义了 `familyAccountId`、`driverId`、`windowOperation`、`secondaryAuthToken` 四个字段，**缺少 `windowPosition`**。这导致车窗操作目标位置信息在 API 层到应用层的反序列化过程中丢失，下游 MQTT `cmd/window/down` 指令的 `windowPosition` 字段（该 Topic Payload 中为必填字段）将无法从应用层传入。

**所在位置**：
- API OOD §1.3 `ControlVehicleWindowRequest` JSON 示例（行 222–235）
- API OOD §4.1 `ControlVehicleWindowReq` 接口（行 1566–1573）
- 对比 `docs/ood_application.md` §4.3 `ControlVehicleWindowRequest`（行 606–610）— 缺少 `windowPosition`

**严重程度**：严重

**改进建议**：
在 `docs/ood_application.md` §4.3 的 `ControlVehicleWindowRequest` DTO 中补充 `windowPosition: WindowPosition` 字段，并在对应枚举中定义 `WindowPosition`（或直接引用 API OOD 已定义的枚举值 `FRONT_LEFT` / `FRONT_RIGHT` / `REAR_LEFT` / `REAR_RIGHT`）。

---

### 问题 2（中等 · 设计死值 · 新增）

**描述**：
§1.3 `IssueSparkRTCTokenRequest.role` 枚举值为 `subscriber | publisher`，但 §1.3 后备注和 §5.1 角色映射均明确：FAMILY 角色仅可请求 `role=subscriber`，`publisher` 角色的 Token 通过 MQTT `cmd/media/join/down` 下发至车机端、不经此 REST 端点。在当前角色体系下（FAMILY / MANAGER / RESCUE），不存在任何角色能合法通过此 REST 端点请求 `role=publisher`。保留此枚举值将导致：(1) 调用方误以为可通过 REST 获取 publisher Token；(2) 后端必须实现额外校验来拒绝 publisher 请求；(3) 与文档自身声明的安全约束自相矛盾。

**所在位置**：
- §1.3 `IssueSparkRTCTokenRequest.role` 说明（行 304）
- §1.3 "安全约束"块（行 306）

**严重程度**：中等

**改进建议**：
二选一：(a) 将 REST 端点的 `role` 取值限定为 `subscriber`，移除 `publisher` 以消除死值；(b) 保留 `publisher` 并在文档中明确列出可请求 `publisher` 的调用方角色及其使用场景。

---

### 问题 3（中等 · 多轮持续性问题 · 接口契约缺失）

**描述**：
S3→S5 手动救援流转（§1.3 行 216–218）依赖 `IEmergencyRescueService.createRescueReport()` 方法生成救援报告并获取 `rescueReportId`。该问题自第 4 轮首次提出以来，历经 5 轮迭代仍未根本解决——`docs/ood_application.md` §3.5 接口方法表中仍无该方法的完整契约（含方法签名、输入/输出 DTO、事务属性、异常处理）。API OOD 中以"⚠ 接口契约待补"标注，但下游 S3 实现者无法据此完整实现编排逻辑。

**所在位置**：
- API OOD §1.3 "S3→S5 手动救援流转说明"块中的"⚠ 接口契约待补"注解（行 218）
- 对比 `docs/ood_application.md` §3.5 接口方法表（行 397–402）— 缺少 `createRescueReport`

**严重程度**：严重（对下游 S3 实现者而言 / 持续性问题）

**改进建议**：
在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入 `CreateRescueReportRequest` DTO、输出 `CreateRescueReportResponse` DTO、事务属性、异常处理），并在 §4.5 补充对应 DTO 定义。完成后移除 API OOD 中的"⚠ 接口契约待补"标注。

---

### 问题 4（一般 · 跨层 DTO 不一致 · 持续性问题）

**描述**：
API OOD 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 均包含 `secondaryAuthToken` 字段，但应用层 OOD `docs/ood_application.md` §4.3 中的同名 DTO 均缺少该字段。二次身份验证凭证在 API→应用层反序列化时丢失，导致应用层无法校验二次验证结果，高敏操作的安全门控链路不完整。该问题自第 4 轮提出后多轮"保留"未解决。

**所在位置**：
- API OOD §1.3 `RequestMediaSessionRequest`（行 154）、`TriggerManualRescueRequest`（行 193）
- 对比 `docs/ood_application.md` §4.3 行 576–579、行 595–598

**严重程度**：中等（对高敏操作安全链路有实际影响）

**改进建议**：
在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

---

### 问题 5（一般 · 跨层 DTO 不一致 · 持续性问题）

**描述**：
API OOD §1.1 `QueryAlertHistoryResponse` 中 `AlertSummary` 包含 `gpsLocation` 可选字段，API OOD §4.1 ArkTS `AlertSummary` 接口也包含 `gpsLocation?: GeoPoint`。但应用层 OOD `docs/ood_application.md` §4.1 `AlertSummary` DTO 缺少 `gpsLocation` 字段。告警的 GPS 位置信息无法传递给应用层及下游前端。

**所在位置**：
- API OOD §1.1 `QueryAlertHistoryResponse` JSON（行 51）
- API OOD §4.1 `AlertSummary` 接口（行 1644–1653）
- 对比 `docs/ood_application.md` §4.1 `AlertSummary`（行 511–517）

**严重程度**：一般

**改进建议**：
在 `docs/ood_application.md` §4.1 `AlertSummary` DTO 中补充 `gpsLocation: Optional<GeoPoint>` 字段及说明。

---

### 问题 6（一般 · 跨层 DTO 不一致 · 持续性问题）

**描述**：
API OOD §1.3 `RequestMediaSessionResponse` 定义了 4 个字段（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），API OOD §4.1 ArkTS `RequestMediaSessionResp` 同样包含全部 4 个字段。但应用层 OOD `docs/ood_application.md` §4.3 同名 DTO 仅定义了 2 个字段（`sessionHandle`、`sessionToken`），缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`，前端无法通过应用层获取 SparkRTC 入房凭证。该问题已在第 7 轮识别，第 9 轮修订说明中标记为"保留"，但应用层仍未修复。

**所在位置**：
- API OOD §1.3 `RequestMediaSessionResponse`（行 166–174）
- 对比 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse`（行 583–585）

**严重程度**：严重（对前端音视频接入功能有阻断性影响）

**改进建议**：
在 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段。

---

### 问题 7（一般 · 跨层 DTO 不一致 · 持续性问题）

**描述**：
API OOD §1.4 `QueryTrajectoryResponse` 包含 `dataConsistency` 字段（取值 `CONSISTENT` / `INCONSISTENT`），用于标注 `vehicleId` 和 `driverId` 参数交叉校验结果。但应用层 OOD `docs/ood_application.md` §4.4 同名 DTO 缺少该字段及 `DataConsistency` 枚举定义。不过应用层 OOD 的 `QueryTrajectoryRequest` 注释中已描述该语义（行 763），仅 DTO 结构定义未补全。

**所在位置**：
- API OOD §1.4 `QueryTrajectoryResponse`（行 390–394）
- 对比 `docs/ood_application.md` §4.4 `QueryTrajectoryResponse`（行 765–768）

**严重程度**：一般

**改进建议**：
在 `docs/ood_application.md` §4.4 `QueryTrajectoryResponse` DTO 中补充 `dataConsistency: DataConsistency` 字段，并新增 `DataConsistency` 枚举（`CONSISTENT` / `INCONSISTENT`）。

---

### 问题 8（轻微 · 版本标识不一致 · 新增）

**描述**：
文档标题行（行 1）为"a_v9 / v9"，文件名（`a_v8_copy_from_v7.md`）为 `a_v8`，而当前实际迭代轮次为第 8 轮。"修订说明（v9）"块（行 2017）所述修正声称"与当前迭代轮次（v9）一致"，但实际仅为第 8 轮。v9 修订应在第 9 轮迭代产出中体现，而非提前声明于第 8 轮。

**所在位置**：
- 行 1（标题）
- 行 2017–2027（修订说明（v9）块）

**严重程度**：轻微

**改进建议**：
将标题从"a_v9 / v9"修正为"a_v8 / v8"，并将"修订说明（v9）"标题更新为"修订说明（v8）"，或在确认本版为 v9 产出后统一文件名和轮次说明。

---

### 问题 9（轻微 · 错误响应文档不完整）

**描述**：
S1（§1.1）和 S5（§1.5）的错误响应小节未列出 `401 Unauthorized`（JWT 无效/过期）状态码。S4（§1.4）显式列出 `401`，S2 和 S3 的错误响应中也未列。虽然 401 通常由 API 网关统一处理，但 API 契约文档如要自称"按 OpenAPI 3.0 风格描述"，应保持各服务错误响应描述的完整性一致（要么都列 401，要么统一说明 401 由网关处理）。

**所在位置**：
- §1.1 错误响应（行 66–69）— 缺 401
- §1.5 错误响应（行 562–564）— 缺 401
- 对比 §1.4 错误响应（行 466–470）— 含 401

**严重程度**：轻微

**改进建议**：
统一各服务错误响应文档策略：要么在所有认证端点中一致列出 401，要么在每个服务小节开篇说明"401 由 API 网关统一处理，不在各端点单独标注"。

---

### 问题 10（轻微 · 持续性问题 · StatusColor 跨层不一致）

**描述**：
API OOD §1.1 `derivedStatusColor` 字段说明（行 37）正确声明了三值（`GREEN` / `YELLOW` / `RED`）与领域层 VO-15 一致，并已标注"若应用层引用额外值（如 `ORANGE`），应以领域层定义为准进行统一"。但实际 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse.derivedStatusColor`（行 498）仍未修正——仍列出 `GREEN / YELLOW / ORANGE / RED` 四值含 `ORANGE`。API OOD 虽已在文档层面指明正确方向，但应用层 OOD 的源头错误尚未修复。

**所在位置**：
- API OOD §1.1 行 37（注释）
- 对比 `docs/ood_application.md` §4.1 行 498

**严重程度**：轻微（API OOD 本身正确，问题在应用层 OOD）

**改进建议**：
在 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse` 中将 `derivedStatusColor` 统一为三值 `GREEN / YELLOW / RED`，与领域层 VO-15 保持一致。

---

## 总结

| 严重程度 | 数量 | 关键问题 |
|:--:|:--:|------|
| 严重 | 3 | 问题 1（新增·windowPosition 缺失）、问题 3（持续·createRescueReport 缺失）、问题 6（持续·RequestMediaSessionResponse 字段缺失） |
| 中等 | 1 | 问题 2（新增·publisher 死值） |
| 一般 | 4 | 问题 4（持续·secondaryAuthToken 缺失）、问题 5（持续·AlertSummary.gpsLocation 缺失）、问题 7（持续·dataConsistency 缺失）、问题 8（新增·版本标识不一致） |
| 轻微 | 3 | 问题 9（新增·错误响应不完整）、问题 10（持续·StatusColor 不一致） |

本文档由 API OOD 产出作者修订、在应用层 OOD 修复后，可投入后续接口层实现使用。建议优先处理三个严重问题（特别是新增的问题 1），否则车窗控制功能将无法正确定位目标车窗。
