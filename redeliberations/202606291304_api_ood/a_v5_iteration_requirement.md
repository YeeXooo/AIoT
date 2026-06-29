根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

本轮审查共识别 8 项问题，按严重程度排列如下：

### 问题 1（严重·接口契约缺失）：S3→S5 手动救援流转引用了未在接口中形式化定义的方法
- **所在位置**：§1.3 TriggerManualRescueResponse 字段说明（:207-209）及"S3→S5 手动救援流转说明"块（:211）
- **问题描述**：产出称"S3 RemoteGuardianshipService 调用 `S5 IEmergencyRescueService.createRescueReport()` 创建救援报告"。经核查，`docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表仅含 `confirmSOSReport`、`issueRescueToken`、`verifyRescueToken`、`queryRescueHistory` 四个方法，不存在 `createRescueReport` 方法签名。但需注意：同一应用层 OOD 的 §4.3 `TriggerManualRescueResponse` DTO（第 601 行）也引用了 `S5.createRescueReport`，说明该方法在设计意图上确应存在，只是尚未在 `IEmergencyRescueService` 接口契约中形式化定义。下游开发者按流转说明实现时会发现目标方法无签名可依、无 DTO 可用。
- **严重程度**：严重 — 阻断 S3→S5 手动救援链路的实现落地，两处文档（API OOD + 应用层 OOD DTO）均已建立对该方法的共识性引用，但接口层缺失形式化契约。
- **改进建议**：在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入/输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 Request/Response DTO 定义。

### 问题 2（一般·跨层不一致）：RequestMediaSessionRequest 的 secondaryAuthToken 字段在应用层 OOD 中缺失
- **所在位置**：§1.3 RequestMediaSessionRequest JSON 示例（:149-156），对比 `docs/ood_application.md` §4.3 RequestMediaSessionRequest（:576-579）
- **问题描述**：API OOD 的 `RequestMediaSessionRequest` 包含 `secondaryAuthToken` 字段（受 §5.2 二次身份验证要求约束），但应用层 OOD 中同名 DTO 仅含 `familyAccountId`、`driverId`、`sessionType` 三个字段，缺少 `secondaryAuthToken`。前端按 API OOD 契约构造请求体传入 `secondaryAuthToken` 后，应用层 DTO 反序列化会丢失该字段，导致安全门控失效。
- **严重程度**：一般 — 影响高敏操作的安全门控链完整性，但下游可在实现阶段发现并修正。
- **改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

### 问题 3（一般·跨层不一致）：TriggerManualRescueRequest 的 secondaryAuthToken 字段在应用层 OOD 中缺失
- **所在位置**：§1.3 TriggerManualRescueRequest JSON 示例（:189-195），对比 `docs/ood_application.md` §4.3 TriggerManualRescueRequest（:595-598）
- **问题描述**：API OOD 的 `TriggerManualRescueRequest` 包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 仅含 `familyAccountId`、`driverId` 两个字段。与问题 2 性质相同——应用层 DTO 缺少高敏操作所需的二次验证凭证字段。
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.3 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

### 问题 4（一般·细节缺失）：高危失能场景下 SparkRTC 会话时长豁免机制未定义
- **所在位置**：§3.2 SparkRTC 房间参数表（:1378）及 Token 签发（:1404）
- **问题描述**：产出规定"最大会话时长 10 分钟（超时自动挂断），高危失能场景下豁免此限制"，同时"Token 有效期 10 分钟，与房间最大会话时长一致"。但未说明：(1) 豁免由哪个组件触发、触发条件是什么；(2) Token 硬编码 10 分钟有效期如何与豁免后的延长会话协调——会话达到 10 分钟后 Token 已过期，音视频流是否会中断；(3) 是否需要重新签发 Token。这导致在 BR-06 碰撞失能等长时间救援场景下，音视频通道可能意外中断。
- **严重程度**：一般 — 影响高危场景下的音视频连续性，但发生在边缘场景（碰撞失能），非高频路径。
- **改进建议**：补充豁免机制的触发条件（由哪个事件驱动、服务端如何判定"高危失能场景"）、Token 续期策略（是否支持自动续签、还是豁免时 Token 不设过期），或在文档中明确标注此场景下的 Token 处理方式为待定项。

### 问题 5（轻微·安全与内部不一致）：车队大屏 WebSocket 端点使用 ws:// 而非 wss://，且同节内表格与代码示例自相矛盾
- **所在位置**：§4.2 看板数据订阅模型表（:1633）与 TypeScript 代码示例（:1643）
- **问题描述**：§4.2 看板数据订阅模型表将车队大屏 WebSocket 端点写作 `ws://api.example.com/ws/fleet?token=<JWT>`（非加密），而 §5.5 加密策略表明文规定"WebSocket 连接 — TLS 1.2+ (WSS)"。更关键的是，同节第 1643 行的 TypeScript 代码示例中已使用 `wss://api.example.com/ws/fleet?token=<JWT>`——表格与代码示例在同一节内自相矛盾。下游实现者无法判断应以哪个为准。
- **严重程度**：轻微 — 属于演示环境 URL 不一致，但会误导实现者和产生安全设计矛盾。
- **改进建议**：将 §4.2 表格中的 `ws://` 统一为 `wss://`，与同节的 TypeScript 代码示例和 §5.5 加密策略保持一致。同时检查全文是否还有其他 `ws://` 引用需要统一。

### 问题 6（轻微·语义模糊）：S3 TriggerManualRescueResponse.status 与 S5 RescueRecordSummary.status 使用不同枚举值表示同一语义状态
- **所在位置**：§1.3 TriggerManualRescueResponse（:199-209）的 `status: PENDING | CONFIRMED | REJECTED`，对比 §1.5 QueryRescueHistoryResponse（:525-543）的 `status: SENT | CONFIRMED | PENDING_RETRY | MANUAL_ESCALATION`
- **问题描述**：家属手动救援触发后，S3 返回 `status=PENDING`（表示"已触发，等待救援中心响应"），而 S5 救援历史中对应记录的状态为 `SENT`（同样表示"已发送至救援中心"）。同一逻辑状态在两个服务中使用不同枚举值，下游需额外维护映射关系，且 `PENDING` 和 `SENT` 哪个是权威状态源不明确。
- **严重程度**：轻微 — 不影响功能正确性，但增加下游理解与实现成本。
- **改进建议**：在 S3→S5 手动救援流转说明中补充状态映射说明（如"S3 `PENDING` 对应 S5 `SENT`"），或统一两处的枚举值命名。

### 问题 7（轻微·格式不一致）：S2 端点表缺失"查询参数"列
- **所在位置**：§1.2 S2 端点表（:79-82），对比 §1.1 S1 端点表（:20-23）
- **问题描述**：S1 端点表包含 8 列（端点、方法、路径、请求体、查询参数、响应体、HTTP 状态码、认证），而 S2 端点表仅 7 列，缺失"查询参数"列。S2 的 `queryInterventionHistory` 端点实际支持 `page`、`size`、`startTime`、`endTime` 查询参数（在表下方正文以自然语言说明），但未在表格中结构化展现。格式不统一可能导致接口层开发人员忽略这些查询参数。
- **严重程度**：轻微
- **改进建议**：为 S2 端点表补充"查询参数"列，将 `page`、`size`、`startTime`、`endTime` 的参数说明移入表格。

### 问题 8（轻微·逻辑张力）：SparkRTC Token 端点的 role 参数包含 publisher 值，但端点归属 FAMILY 角色
- **所在位置**：§1.3 IssueSparkRTCTokenRequest（:285-296）的 `role: subscriber | publisher`，对比 §5.1 角色→权限映射将 `sparkrtc/token` 端点归入 FAMILY 可访问范围
- **问题描述**：`role` 参数接受 `publisher`（车机端），但端点自身按 §5.1 仅 FAMILY 角色可调用。虽然实际流程中车机端通过 MQTT `cmd/media/join/down` 接收 Token 而非调用此 REST 端点，但端点契约中存在一个 FAMILY 调用方可注入的 `publisher` 参数，形成不必要的攻击面——恶意 FAMILY 用户可能尝试传入 `publisher` 角色获取不应有的权限。
- **严重程度**：轻微 — 实际风险低（后端应校验调用方身份而非信任 role 参数），但契约设计不够干净。
- **改进建议**：在 IssueSparkRTCTokenRequest 说明中补充"后端须校验调用方角色与请求中 role 的一致性，FAMILY 角色调用方仅可请求 role=subscriber"；或考虑将 role 字段从请求体中移除，由服务端根据 JWT 中的 AccountRole 隐式推导。

## 历史迭代回顾

第 1-3 轮迭代中识别的问题已全部解决，本轮无持续存在的问题。当前 8 项问题均为本轮新发现：

- **已解决的问题（第 1 轮）**：S3 家属权限查询端点已补充；MQTT Payload JSON Schema 已全面覆盖。
- **已解决的问题（第 2 轮）**：新增 API 端点已映射到应用层方法（v5 更新）；手动救援归属已添加设计说明；S5 认证标注已统一；SparkRTC Token 消费者已阐明；回滚响应已覆盖 ROLLING_BACK 状态；报告下载已补充 Content-Type；MQTT 主题变量语法已统一；DELETE 响应码策略已在开篇统一说明。
- **已解决的问题（第 3 轮）**：rescueRequestId/rescueReportId ID 断裂已修复（含完整流转说明和双 ID 返回）；3 个预签名方法已在应用层 OOD 补充完整签名及 DTO；familyAccountId 安全门控规则已添加；cmd/media/join/down MQTT 主题已在路由表和 Payload 中补充；QueryTrajectoryResponse dataConsistency 字段已补充；§4.1 requestMediaSession 端点已补充。
- **新发现的问题（第 4 轮 v2）**：上述问题 1-8 均为本轮新发现，重点解决 createRescueReport 接口形式化定义、两处 DTO 的 secondaryAuthToken 字段缺失、SparkRTC 豁免机制定义、ws:// vs wss:// 一致性、枚举语义统一、S2 表格格式及 role 参数安全张力。

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v4_output_v2.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
