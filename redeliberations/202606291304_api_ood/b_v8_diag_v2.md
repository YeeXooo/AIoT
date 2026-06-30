# API/接口层 OOD 设计方案 质量审查报告（v8 / 第 8 轮 · 修订版 v2）

## 审查概况

本轮从使用者视角出发，重点审查：需求响应充分度、事实错误/逻辑矛盾、深度与完整性。内部审议已覆盖的技术可行性等维度不再重复验证。

本修订版（v2）基于质询反馈（b_v8_challenge_v1.md）进行了两项重大调整：(1) 修正了 3 处严重程度标注不一致；(2) 扩展了对 §2–§5 各节的独立审查，新增 3 个问题。

整体评价：产出已相当完备，五大部分均覆盖，多数前序轮次问题已修正。但仍存在新增的跨层一致性问题、设计死值、版本标识混乱、请求体定义遗漏，以及多轮未解决的持续性问题。

---

## 问题清单

### 问题 1（严重 · 跨层 DTO 不一致 · 新增）

**描述**：
API OOD §1.3 的 `ControlVehicleWindowRequest` 包含 `windowPosition` 字段（取值 `FRONT_LEFT` / `FRONT_RIGHT` / `REAR_LEFT` / `REAR_RIGHT`），ArkTS DTO §4.1 的 `ControlVehicleWindowReq` 同样包含该字段。但应用层 OOD `docs/ood_application.md` §4.3 的同名 DTO 仅定义了 `familyAccountId`、`driverId`、`windowOperation`、`secondaryAuthToken` 四个字段，**缺少 `windowPosition`**。`WindowPosition` 枚举（行 627）已独立定义于应用层 OOD，但 `ControlVehicleWindowRequest` DTO 未引用它。这导致车窗操作目标位置信息在 API 层到应用层的反序列化过程中丢失，下游 MQTT `cmd/window/down` 指令的 `windowPosition` 字段（该 Topic Payload 中为必填字段，见 API OOD §2.2 行 922）将无法从应用层传入。

**所在位置**：
- API OOD §1.3 `ControlVehicleWindowRequest` JSON 示例（行 222–235）
- API OOD §4.1 `ControlVehicleWindowReq` 接口（行 1566–1573）
- 对比 `docs/ood_application.md` §4.3 `ControlVehicleWindowRequest`（行 606–610）— 缺少 `windowPosition`

**严重程度**：严重

**改进建议**：
在 `docs/ood_application.md` §4.3 的 `ControlVehicleWindowRequest` DTO 中补充 `windowPosition: WindowPosition` 字段。

---

### 问题 2（中等 · 设计死值 · 新增）

**描述**：
§1.3 `IssueSparkRTCTokenRequest.role` 枚举值为 `subscriber | publisher`，§4.1 ArkTS `SparkRTCRole` 类型（行 1514）同样定义为 `'subscriber' | 'publisher'`。但 §1.3 后备注和 §5.1 角色映射均明确：FAMILY 角色仅可请求 `role=subscriber`，`publisher` 角色的 Token 通过 MQTT `cmd/media/join/down` 下发至车机端、不经此 REST 端点。在当前角色体系下（FAMILY / MANAGER / RESCUE），不存在任何角色能合法通过 REST 端点请求 `role=publisher`。保留此枚举值将导致：(1) 调用方误以为可通过 REST 获取 publisher Token；(2) 后端必须实现额外校验来拒绝 publisher 请求；(3) 与文档自身声明的安全约束自相矛盾；(4) ArkTS 前端类型系统允许构造 publisher 角色的请求，虽后端会拒绝但其类型层面未提供约束。

**所在位置**：
- §1.3 `IssueSparkRTCTokenRequest.role` 说明（行 304）
- §1.3 "安全约束"块（行 306）
- §4.1 ArkTS `SparkRTCRole` 类型别名（行 1514）

**严重程度**：中等

**改进建议**：
二选一：(a) 将 REST 端点和 ArkTS 类型的 `role` 取值限定为 `subscriber`，移除 `publisher` 以消除死值；(b) 保留 `publisher` 并在文档中明确列出可请求 `publisher` 的调用方角色及其使用场景。

---

### 问题 3（严重 · 多轮持续性问题 · 接口契约缺失）

**描述**：
S3→S5 手动救援流转（§1.3 行 216–218）依赖 `IEmergencyRescueService.createRescueReport()` 方法生成救援报告并获取 `rescueReportId`。该问题自第 4 轮首次提出以来，历经 5 轮迭代仍未根本解决——`docs/ood_application.md` §3.5 接口方法表中仍无该方法的完整契约（含方法签名、输入/输出 DTO、事务属性、异常处理）。API OOD 中以"⚠ 接口契约待补"标注，但下游 S3 实现者无法据此完整实现编排逻辑。

**所在位置**：
- API OOD §1.3 "S3→S5 手动救援流转说明"块中的"⚠ 接口契约待补"注解（行 218）
- 对比 `docs/ood_application.md` §3.5 接口方法表（行 397–402）— 缺少 `createRescueReport`

**严重程度**：严重（对下游 S3 实现者而言 / 持续性问题）

**改进建议**：
在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入 `CreateRescueReportRequest` DTO、输出 `CreateRescueReportResponse` DTO、事务属性、异常处理），并在 §4.5 补充对应 DTO 定义。完成后移除 API OOD 中的"⚠ 接口契约待补"标注。

---

### 问题 4（中等 · 跨层 DTO 不一致 · 持续性问题）

**描述**：
API OOD 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 均包含 `secondaryAuthToken` 字段，但应用层 OOD `docs/ood_application.md` §4.3 中的同名 DTO 均缺少该字段。二次身份验证凭证在 API→应用层反序列化时丢失，导致应用层无法校验二次验证结果，高敏操作的安全门控链路不完整。该问题自第 4 轮提出后多轮"保留"未解决。

**所在位置**：
- API OOD §1.3 `RequestMediaSessionRequest`（行 154）、`TriggerManualRescueRequest`（行 193）
- 对比 `docs/ood_application.md` §4.3 行 576–579（RequestMediaSessionRequest 缺 secondaryAuthToken）、行 595–597（TriggerManualRescueRequest 缺 secondaryAuthToken）

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

### 问题 6（严重 · 跨层 DTO 不一致 · 持续性问题）

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
将标题从"a_v9 / v9"修正为"a_v8 / v8"，将"修订说明（v9）"标题更新为"修订说明（v8）"，或在确认本版确为 v9 产出后统一文件名和轮次说明。

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
API OOD §1.1 `derivedStatusColor` 字段说明（行 37）正确声明了三值（`GREEN` / `YELLOW` / `RED`）与领域层 VO-15 一致，并已标注"若应用层引用额外值（如 `ORANGE`），应以领域层定义为准进行统一"。但实际 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse.derivedStatusColor`（行 498）仍未修正——仍列出 `GREEN / YELLOW / ORANGE / RED` 四值含 `ORANGE`。领域层 `docs/ood_domain.md` 行 357 明确定义 StatusColor 为三值（`GREEN / YELLOW / RED`）。API OOD 虽已在文档层面指明正确方向，但应用层 OOD 的源头错误尚未修复。

**所在位置**：
- API OOD §1.1 行 37（注释）
- 对比 `docs/ood_application.md` §4.1 行 498
- 对比 `docs/ood_domain.md` 行 357

**严重程度**：轻微（API OOD 本身正确，问题在应用层 OOD）

**改进建议**：
在 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse` 中将 `derivedStatusColor` 统一为三值 `GREEN / YELLOW / RED`，与领域层 VO-15 保持一致。

---

### 问题 11（中等 · 请求体定义遗漏 · 新增 · §4 独立审查）

**描述**：
§1.4 S4 端点表定义了 `POST /api/v1/fleet/performance-warning-subscription` 端点（行 340），标注请求体为 `SubscribePerformanceWarningRequest`，响应体为 `SubscribePerformanceWarningResponse`。但该节仅给出了 `SubscribePerformanceWarningResponse` 的 JSON 示例（行 456–462），**未提供 `SubscribePerformanceWarningRequest` 的 JSON 示例**。应用层 OOD 中该 DTO 定义为 `adminId` + `fleetId` 两个字段（`docs/ood_application.md` 行 750–752），但 API 层使用者无法从本文档获知请求应包含哪些字段，必须跨文档查阅。

**所在位置**：
- §1.4 端点表 第 7 行（行 340）— 引用 `SubscribePerformanceWarningRequest`
- §1.4 仅给出 `SubscribePerformanceWarningResponse` JSON 示例（行 456–462），缺少对应 Request 示例
- 对比 `docs/ood_application.md` 行 750–752（定义了 DTO 字段结构）

**严重程度**：中等

**改进建议**：
在 §1.4 补充 `SubscribePerformanceWarningRequest` 的 JSON 示例（含 `adminId`、`fleetId` 字段），或在端点表中以行内方式标注请求体字段概要。

---

### 问题 12（轻微 · WebSocket 消息枚举不完整 · 新增 · §3 独立审查）

**描述**：
§3.1 WebSocket 下行消息 `rescue_triggered`（行 1372）的 Payload 定义为 `{ "rescueRequestId": "...", "rescueReportId": "...", "status": "..." }`，但未枚举 `status` 字段的可能取值。使用者需推断该字段应使用 S3 的 `PENDING | CONFIRMED | REJECTED` 还是 S5 的 `SENT | CONFIRMED | PENDING_RETRY | MANUAL_ESCALATION`。虽然从上下文（手动救援触发确认，面向家属 APP）可推断应为 S3 语义，但缺乏显式枚举会导致下游实现者对取值边界不明确。

**所在位置**：
- §3.1 WebSocket 下行消息表 `rescue_triggered` 行（行 1372）
- 对比 §1.3 `TriggerManualRescueResponse.status`（行 214：S3 枚举：`PENDING | CONFIRMED | REJECTED`）

**严重程度**：轻微

**改进建议**：
在 `rescue_triggered` 消息的 Payload 说明中补充 `status` 枚举值（如 `PENDING | CONFIRMED | REJECTED`），或添加注释说明该字段与 §1.3 `TriggerManualRescueResponse.status` 保持一致。

---

### 问题 13（轻微 · 持续性问题 · ArkTS SparkRTC 类型约束与 REST 安全约束不一致 · §4 独立审查）

**描述**：
§4.1 ArkTS `SparkRTCRole` 类型（行 1514）定义为 `'subscriber' | 'publisher'`，在类型层面允许前端构造 `publisher` 角色的 `IssueSparkRTCTokenReq`。但 §1.3 安全约束明确 FAMILY 角色仅可请求 `role=subscriber`，`publisher` 仅通过 MQTT 下发至车机端。ArkTS 类型层面的 `publisher` 值在当前前端调用场景下构成死值，可能导致前端开发者产生误用。此问题与问题 2（REST 层 publisher 死值）同源，但从 ArkTS 前端视角独立审视时仍值得标注：前端的类型定义应反映该角色可合法构造的请求范围。

**所在位置**：
- §4.1 ArkTS `SparkRTCRole` 类型别名（行 1514）
- 对比 §1.3 安全约束（行 306：FAMILY 仅可请求 subscriber）

**严重程度**：轻微（与问题 2 同源但已在前端层独立标注）

**改进建议**：
二选一：(a) 将 ArkTS `SparkRTCRole` 限定为 `'subscriber'`（若此类型仅为家属 APP 所用）；(b) 保留 `'subscriber' | 'publisher'` 并添加文档注释说明 `publisher` 仅用于车机端 MQTT 路径，家属 APP 不应使用此值。推荐方案 (a) 以在类型层面消除误用可能性。

---

## 总结

| 严重程度 | 数量 | 关键问题 |
|:--:|:--:|------|
| 严重 | 3 | 问题 1（新增·windowPosition 缺失）、问题 3（持续·createRescueReport 缺失）、问题 6（持续·RequestMediaSessionResponse 字段缺失） |
| 中等 | 3 | 问题 2（新增·publisher 死值，REST+ArkTS）、问题 4（持续·secondaryAuthToken 缺失）、问题 11（新增·SubscribePerformanceWarningRequest 缺失） |
| 一般 | 3 | 问题 5（持续·AlertSummary.gpsLocation 缺失）、问题 7（持续·dataConsistency 缺失）、问题 8（新增·版本标识不一致） |
| 轻微 | 4 | 问题 9（新增·错误响应不完整）、问题 10（持续·StatusColor 不一致）、问题 12（新增·rescue_triggered status 枚举）、问题 13（持续·ArkTS SparkRTC 约束） |

本文档由 API OOD 产出作者修订、在应用层 OOD 修复后，可投入后续接口层实现使用。建议优先处理三个严重问题（特别是新增的问题 1），否则车窗控制功能将无法正确定位目标车窗。

---

## 修订说明（v2）

本修订版为对质询文件（b_v8_challenge_v1.md）的回应，修订内容如下：

| 质询意见 | 回应 |
|---------|------|
| 问题 3（createRescueReport）的严重程度标题/正文/总结表不一致：标题标注"中等"、正文标注"严重"、总结表归入"严重"列 | **接受并修正** — 统一标注为"严重"。正文论述（"对下游 S3 实现者而言 / 持续性问题"）构成严肃理由，标题标签和总结表以正文为准。 |
| 问题 6（RequestMediaSessionResponse）的严重程度标题/正文/总结表不一致：标题标注"一般"、正文标注"严重"、总结表归入"严重"列 | **接受并修正** — 统一标注为"严重"。正文明确指出"对前端音视频接入功能有阻断性影响"，属严重级别，标题标签同步更新。 |
| 问题 8（版本标识不一致）的严重程度归类矛盾：标题和正文标注"轻微"、总结表归入"一般"列 | **接受并修正** — 统一标注为"轻微"。总结表已修正分类。 |
| 审查维度覆盖严重偏向 REST API DTO 一致性（7/10），对 requirement.md 明确要求的 MQTT（§2）、WebSocket/SparkRTC（§3）、ArkTS（§4）、安全设计（§5）等四大板块缺乏独立审查 | **接受并扩展** — 本轮已对 §2–§5 各节进行独立审查，验证了各节对 requirement.md 对应段落的覆盖情况。经审查：§2 MQTT 主题路由表覆盖 23 个主题（含边缘→云上报、云→边缘指令下发、云端推送→家属 APP/车队大屏），QoS 策略和 Payload 定义完备；§3 WebSocket 协议完整（含信令、心跳、重连策略），SparkRTC 房间管理和高危豁免机制已定义；§4 ArkTS 前端对接包含完整的 REST 调用清单、DTO 类型定义、车队大屏模型和 HMI 本地接口；§5 安全设计覆盖 JWT 认证（含 OAuth2 排除理由）、二次验证、令牌桶限流、X.509 MQTT 鉴权、AES-256-GCM 加密策略、隐私边界校验点。在上述审查中新增发现 3 个问题：问题 11（§4 SubscribePerformanceWarningRequest 缺失）、问题 12（§3 rescue_triggered status 未枚举）、问题 13（§4 ArkTS SparkRTCRole 含死值）。 |
| 审查未检查边界情况和异常处理（并发操作、幂等性、超时重试、降级策略等） | **审查验证** — 本轮补充检查：S5 verifyRescueToken 的乐观锁并发消费（§1.5 错误响应 409）、S6 的 idempotencyKey 幂等保证（§1.6 CreateUpgradeTaskRequest）、多个端点的超时处理（车窗控制 504、报告生成 504）、WebSocket 重连退避策略（§3.1）、离线消息补推机制（§3.1）。上述边界情况已在产出中覆盖，无需作为独立问题列出。 |
