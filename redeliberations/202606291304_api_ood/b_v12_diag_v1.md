# API/接口层 OOD 质量审查报告（v12）

> 审查对象：`a_v12_copy_from_v11.md`
> 审查视角：使用者视角（产出是否可直接投入使用、需求响应充分度、深度和完整性）
> 当前迭代轮次：第 12 次

---

## 一、问题清单

### 问题 1：MQTT 干预指令 Topic 存在无对应 API 端点的用例描述

- **问题描述**：§2.1 主题路由总表中 `{deviceId}/cmd/intervention/down` 话题的 Payload 说明为"InterventionInstruction 集合（云端关联下发干预指令，如车队管理员远程鸣笛等）"，暗示存在"车队管理员远程鸣笛"的下行指令用例。但在 §1.2 S2 InterventionService 和 §1.4 S4 FleetManagementService 的 REST API 端点表中，均无任何端点支持车队管理员发起干预指令下发。S2 仅提供干预状态查询和历史查询（只读），S4 的端点均为看板/报告/订阅（不涉及指令下发）。此描述会导致下游实现者误以为存在对应的 API 触发入口，实际上该 Topic 在云端侧的触发入口是缺失的。
- **所在位置**：§2.1 主题路由总表，`{deviceId}/cmd/intervention/down` 行（Payload 说明列）
- **严重程度**：中等
- **改进建议**：二选一：(a) 若车队管理员远程干预确为需求场景，在 S2 或 S4 补充对应的 REST 端点（如 `POST /api/v1/fleet/{fleetId}/interventions`）；(b) 若当前不覆盖远程干预，将该 Payload 说明中的"如车队管理员远程鸣笛等"移除，改为仅描述由 S1 风险判定驱动的干预指令下发场景，与 §1.2 应用层方法 `queryInterventionStatus`/`queryInterventionHistory`（只读）保持一致。

### 问题 2：`RequestMediaSessionResponse` 中 `sessionToken` 与 `sparkRTCJoinToken` 双字段语义重叠

- **问题描述**：§1.3 `RequestMediaSessionResponse` 同时包含 `sessionToken`（描述为"家属端接入 SparkRTC 的临时鉴权 token"）和 `sparkRTCJoinToken`（描述为"前端加入 SparkRTC 房间的 join token"）。两个字段的语义高度重叠——均为家属端接入 SparkRTC 房间的凭证。§3.1 WebSocket `access_granted` 下发消息同时携带这两个字段，§4.1 ArkTS 类型 `RequestMediaSessionResp` 也保留了两者，但文档未说明两者的区别和使用场景。API 使用者无法确认该用哪个字段调用 SparkRTC 客户端 SDK 的 join 方法，存在误用风险。
- **所在位置**：§1.3 `RequestMediaSessionResponse` JSON 示例（行 186–192）、§3.1 `access_granted` 下行消息（行 1490）、§4.1 ArkTS `RequestMediaSessionResp`（行 1660–1665）
- **严重程度**：中等
- **改进建议**：明确区分两个字段的用途和使用时机。例如：`sessionToken` 为会话级授权凭证（用于后续媒体会话管理操作如挂断），`sparkRTCJoinToken` 为实际传入 SparkRTC SDK 的入房 Token。在 §1.3 各字段说明中补充此区分，并在 §4.1 ArkTS 代码示例中明确展示 `sparkRTCClient.joinRoom(msg.sparkRTCRoomId, msg.sparkRTCJoinToken)`——而非使用 `sessionToken`。

### 问题 3：§4.1 家属 APP REST API 调用清单缺少认证端点

- **问题描述**：需求 `requirement.md:38` 要求 §4 ArkTS 前端对接契约覆盖"家属 APP（HarmonyOS）调用的全部后端接口清单"。§4.1 当前仅列出 10 个 S3 相关端点及 1 个 S1 端点，但家属 APP 必然需要调用的 `POST /api/v1/auth/login`（获取 JWT）、`POST /api/v1/auth/refresh`（刷新 Token）、`POST /api/v1/auth/secondary-verify`（二次身份验证）三个认证端点均未列入。家属 APP 开发者仅阅读 §4.1 无法获知完整的接口调用序列。
- **所在位置**：§4.1 REST API 调用列表（行 1606–1616），对比 §1.7 Auth 端点表（行 736–835）
- **严重程度**：中等
- **改进建议**：在 §4.1 REST API 调用列表中补充三个 Auth 端点行（`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/secondary-verify`），标注每个端点的请求/响应模型引用。若为保持 §4.1 的业务聚焦性，也应在列表前添加说明块，明确交叉引用 §1.7 中的认证端点。

### 问题 4：§5.1 JWT `scope` 字段与角色权限模型的关系未定义

- **问题描述**：§5.1 JWT Payload 示例包含 `"scope": ["read:risk-status", "write:window-control"]`（行 1918），但同一节的角色→权限映射表仅定义了 `FAMILY` / `MANAGER` / `RESCUE` 三个角色到端点范围的映射关系，未说明 scope 的角色分配规则、scope 与角色的优先级，以及 scope 是否随角色动态生成。API 实现者无法确定授权判断应基于 JWT `role` 声明还是 `scope` 声明，或两者如何协同。
- **所在位置**：§5.1 JWT Token 结构（行 1912–1921），对比 §5.1 角色→权限映射表（行 1933–1937）
- **严重程度**：一般
- **改进建议**：二选一：(a) 若 scope 用于细粒度权限控制，补充 scope 与角色映射表（如 `FAMILY` → `["read:risk-status", "write:window-control", "read:guardianship-permissions"]`），并说明应用服务入口校验规则（角色匹配 + scope 包含）；(b) 若当前仅用角色做权限判断，移除 JWT Payload 中的 `scope` 字段，避免引入未定义的设计元素。

### 问题 5：缺乏数据生命周期/保留策略

- **问题描述**：系统处理驾驶员生理体征、GPS 轨迹、车内音视频等高度敏感数据（受 BR-04 隐私边界约束），但文档仅在 §5.5 加密策略表的个别行中零散提及保留期（路怒语音存证 90 天自动删除)，在 §3.1 提及离线消息队列保留 7 天。告警历史、行程评分、生理体征快照、GPS 轨迹、家属监护关系等关键数据的保留周期、归档策略和到期清理规则均未定义。API 设计文档作为下游实现的指导性文件，缺失数据生命周期策略会导致数据存储方案无法合理规划。
- **所在位置**：全文（缺失）
- **严重程度**：一般
- **改进建议**：在 §5.6 隐私边界安全校验点之后新增"数据生命周期"小节，至少覆盖以下类别的最小保留策略：告警历史、行程记录及评分、生理体征快照、车辆遥测数据、家属监护关系、救援记录。每类数据明确保留时长及到期处理方式（删除 / 匿名化 / 归档），与 BR-04 隐私边界规则对齐。

### 问题 6：S4 `QueryTrajectoryResponse` 缺少时间范围约束和分页限制

- **问题描述**：§1.4 `GET /api/v1/fleet/{fleetId}/trajectory` 端点的查询参数含 `startTime`/`endTime`，但未声明：(1) 最大查询时间跨度（如最长 7 天）；(2) 当时间范围过大时 `totalCount` 可能巨大（轨迹点可上万），对服务端内存和响应时间的影响；(3) `page`/`size` 的上限约束。相比之下，S1 `queryAlertHistory` 明确标注了 `size` 的 max 100（行 80），S6 OTA 也约束批量上限 100 辆。S4 轨迹查询作为可能的数据密集端点，缺乏同等约束。
- **所在位置**：§1.4 端点表 `车辆轨迹查询` 行（行 364）及查询参数说明
- **严重程度**：一般
- **改进建议**：补充查询参数约束：(a) `startTime` 到 `endTime` 的最大跨度（如 ≤30 天）；(b) `size` 的默认值和上限（如默认 100，最大 500）；(c) 当 `totalCount` 超过某阈值时的截断处理策略或分页建议。

### 问题 7：§4.1 ArkTS WebSocket 消息模型缺少 `token_renewed` 类型定义

- **问题描述**：§3.1 下行消息表定义了 `token_renewed` 消息类型（行 1494），用于高危失能场景下 SparkRTC Token 自动续签时推送至家属 APP。§4.1 ArkTS 数据模型定义区包含了 `DriverStatusSnapshot`、`AlertSummary`、`AccessGrantedMessage`、`AccessRevokedMessage` 四个 WebSocket 消息模型（行 1742–1788），但遗漏了 `TokenRenewedMessage` 的 TypeScript 接口定义。此外，`subscribe_status_ack`（行 1492）和 `rescue_triggered`（行 1493）的消息模型也未纳入 §4.1。
- **所在位置**：§4.1 ArkTS 数据模型定义（行 1741–1788），对比 §3.1 下行消息表（行 1484–1495）
- **严重程度**：一般
- **改进建议**：在 §4.1 ArkTS 接口定义中补充 `TokenRenewedMessage`（字段：`sparkRTCRoomId`, `sparkRTCJoinToken`, `expiresAt`）、`SubscribeStatusAckMessage`（字段：`subscriptionId`, `initialSnapshot: DriverStatusSnapshot`）和 `RescueTriggeredMessage`（字段：`rescueRequestId`, `rescueReportId`, `status: RescueRequestStatus`）三个接口的 TypeScript 定义。

### 问题 8：§5.6 隐私边界校验表缺少家属告警推送中的 GPS 权限校验规则

- **问题描述**：§5.6 隐私校验表包含"家属查询驾驶员位置 | S3 subscribeDriverStatus 入口 | 仅当家属持有有效监护关系 + 权限通过时返回 GPS，无权限时 GPS 字段为 `null`"，覆盖了主动查询（pull）场景。但 §2.1 的 `family/{accountId}/alert/push` 和 `family/{accountId}/status/push` 两个 MQTT 推送（push）Topic 同样包含 GPS 位置信息（`gpsLocation` 字段），未见对应的推送阶段 GPS 脱敏/过滤规则。若家属监护权限在告警触发时已过期或撤销，推送消息中的 GPS 是否仍应携带？需补充说明。
- **所在位置**：§5.6 隐私边界安全校验点表（行 2027–2036），对比 §2.2 家属告警推送 AlertTriggeredEvent（行 1378）、§2.2 DriverStatusSnapshot（行 952–966）
- **严重程度**：轻微
- **改进建议**：在 §5.6 表中新增一行：`家属告警/状态推送中的 GPS 位置 | MQTT family/{accountId}/alert/push 和 family/{accountId}/status/push 推送前（S3 Gateway） | 推送时校验家属当前监护权限有效性；无权限时 GPS 字段填 null，仅推送告警摘要不含位置`。

---

## 二、整体评价

产出在 REST API 契约完整性、MQTT 主题路由覆盖度、WebSocket/SparkRTC 信令集成和 ArkTS 前端对接模型方面达到了较高的完成度。经 12 轮迭代，反复出现的问题（如跨层 DTO 不一致、缺失的端点和错误响应码）已大部分修复。当前 v12 版本的主要质量短板集中在：(1) 单个 Topic 描述引用了未实现的 API 功能（问题 1）；(2) API 消费者视角的信息完整性问题——`sessionToken`/`sparkRTCJoinToken` 双字段语义不清晰（问题 2）、前端清单缺失认证端点（问题 3）；(3) 作为生产级设计文档仍缺少数据生命周期策略（问题 5）。所有 8 个问题的严重程度在轻微至中等，无阻塞性事实错误或重大逻辑矛盾。
