# API/接口层 OOD 设计方案 — 质量审查报告（v11）

> 审查对象：`a_v11_copy_from_v10.md`（第 11 轮迭代产出）
> 审查轮次：首轮（v11 首次诊断）
> 审查维度：需求响应充分度、事实错误/逻辑矛盾、深度与完整性（侧重内部审议未充分覆盖的维度）

---

## 一、问题诊断

### 问题 1（中等·认证链路不完整）：JWT refresh token 端点缺失

- **问题描述**：`LoginResponse`（§1.7）包含 `refreshToken` 字段，§5.1 签发与校验流程第 5 步明确描述"JWT 过期前 5 分钟，客户端可携带 refresh token 换取新 JWT"，但 §1.7 和整个 §1 均未定义 `POST /api/v1/auth/refresh` 端点。客户端收到 refreshToken 后无合法端点消费该凭证，致使 JWT 刷新链路在 API 契约层面断裂——客户端只能等 token 过期后重新登录。
- **所在位置**：§1.7 Auth 端点表（行 713–716），对比 §5.1 步骤 5（行 1882）
- **严重程度**：中等 — 不影响核心业务功能，但导致客户端侧 JWT 生命周期管理不可操作，实际使用中客户端将频繁触发重新登录
- **改进建议**：在 §1.7 新增 `POST /api/v1/auth/refresh` 端点，定义 `RefreshTokenRequest`（含 `refreshToken: String`）和 `RefreshTokenResponse`（含新的 `accessToken`、`refreshToken`、`expiresIn`），参照现有 `POST /api/v1/auth/login` 的端点定义风格

---

### 问题 2（中等·错误响应不完整）：S5 和 S6 错误响应列表缺少基础错误码

- **问题描述**：S5（§1.5）错误响应仅列出 `403`（授权凭证过期/已消费/角色不匹配）和 `409`（并发消费冲突）；S6（§1.6）仅列出 `400`（批量超限）和 `409`（任务冲突）。对比 S1（400/404/503）、S3（401/403/409/503/504）、S4（400/404/504），S5 缺少 `400`（queryRescueHistory 的查询参数无效）和 `404`（driverId/vehicleId 不存在），S6 缺少 `404`（vehicleId 不存在于 queryUpgradeHistory）、`503`（IoTDA 下指令通道不可达）等。API 使用者无法获知 queryRescueHistory 和 queryUpgradeHistory 的完整错误场景。
- **所在位置**：§1.5 错误响应（行 582–584）、§1.6 错误响应（行 703–706）
- **严重程度**：中等 — 直接降低 API 可消费性，调用方需从上下文或代码推断错误场景
- **改进建议**：在 §1.5 补充 `400`（查询参数无效）和 `404`（驾驶员/车辆不存在）；在 §1.6 补充 `404`（车辆/任务不存在）和 `503`（IoTDA 通道不可达，参照应用层 OOD §3.6 的 `AppError.IoTDAChannelFailure`）

---

### 问题 3（一般·认证链路不完整）：LoginRequest JSON 字段设计冗余

- **问题描述**：`LoginRequest`（§1.7）JSON 示例同时包含 `credential`/`secret` 和 `phone`/`smsCode` 两组字段。文档正文解释：`PASSWORD` 模式使用 `credential`（用户名/邮箱）+ `secret`（密码），`SMS_CODE` 模式使用 `credential`（手机号）+ `secret`（验证码）。但独立存在的 `phone` 和 `smsCode` 为 null 值字段与 `credential`/`secret` 的负载复用形成语义冗余——API 消费者需额外理解"为什么有两种方式表示同一个手机号/验证码"，增加了不必要的认知负担。同时，若客户端同时传入非 null 的 `credential` 和 `phone` 且值不同，后端行为未定义。
- **所在位置**：§1.7 LoginRequest JSON 示例（行 720–727）、枚举说明（行 730–732）
- **严重程度**：一般 — 不影响功能正确性，但损害 API 契约清晰度
- **改进建议**：二选一：(a) 移除 `phone` 和 `smsCode` 字段，仅保留 `credential` + `secret` + `authMethod` 的三字段设计，两种模式共用同组字段（credential 的语义由 authMethod 决定，已在正文中说明）；(b) 若确实需要区分，则 `PASSWORD` 模式使用 `credential`+`secret`，`SMS_CODE` 模式使用 `phone`+`smsCode`，避免两组字段同时存在于 JSON 示例中

---

### 问题 4（一般·API 契约完整性问题）：错误响应体结构未定义

- **问题描述**：文档在多处引用具体错误码（如 `BatchSizeExceeded`、`UpgradeTaskNotCancellable`、`SecondaryAuthRequired`、`PermissionDenied(NotRelated)`、`AppError.ReportGenerationTimeout` 等），但整份 API OOD 未定义 REST API 的标准错误响应体格式。API 消费者无从知晓错误响应是 `{ "errorCode": "BatchSizeExceeded", "message": "..." }` 还是其他结构。仅 §3.1 WebSocket 下行消息的 `error` 类型隐式定义了 `{ "code": "...", "message": "..." }` 格式，但该格式未必适用于 REST 端点。此问题是接口设计完整性的关键遗漏——应用层 OOD 已定义了完整的 `AppError` 枚举（`docs/ood_application.md` §6.1），但 API OOD 未承接为 REST 错误响应契约。
- **所在位置**：§1 各节错误响应块（行 68–71、116–119、329–335、487–490、582–584、703–706），对比应用层 OOD §6.1 AppError 枚举
- **严重程度**：一般 — 不阻塞理解核心 API 路径，但调用方无法编写统一的错误处理代码
- **改进建议**：在 §一 总述段落后（与 DELETE 响应码约定、401 统一处理约定并列）新增"**REST 错误响应体约定**"块，定义统一格式。建议为：`{ "errorCode": "BatchSizeExceeded", "message": "单次最多 100 辆车", "requestId": "trace-uuid" }`，并明确 `errorCode` 取值映射到应用层 `AppError` 枚举变体名

---

### 问题 5（一般·完整性）：S4 绩效预警订阅缺少取消订阅端点

- **问题描述**：S4 提供 `POST /api/v1/fleet/performance-warning-subscription` 用于订阅车队绩效预警，但无对应的 `DELETE` 端点取消订阅。需求（`requirement.md:27`）要求 S4 覆盖"绩效预警订阅"，订阅的完整语义应包含开通和关闭两个方向的管控。当前设计下，管理员一旦订阅即无法主动退订，仅能断开 WebSocket 连接来被动终止推送（且 WebSocket 重连后订阅是否恢复未定义）。
- **所在位置**：§1.4 S4 端点表（行 350），对比 S3 的 DELETE 对称设计（`POST media-session` + `DELETE media-session/{sessionHandle}`）
- **严重程度**：一般 — 不影响核心业务功能，但损害 API 的 CRUD 对称性和运维友好性
- **改进建议**：在 §1.4 S4 端点表中新增 `DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId}` 端点，或通过 PUT 更新订阅状态（`{ "active": false }`）

---

### 问题 6（轻微·安全设计不完整）：令牌桶容量参数未定义

- **问题描述**：§5.3 接口限流策略采用令牌桶算法，文档列出各层级的速率限制（如家属角色 30 req/s），并声明"突发容忍度为桶容量的 1.5 倍"，但**未定义任何层级的桶容量（bucket capacity）**。令牌桶算法需要两个核心参数——速率（rate）和容量（capacity），仅有速率参数无法完成算法配置（实现者无法确定"桶最大可积累多少令牌"），"1.5 倍"的比例系数也因基数未知而失去意义。
- **所在位置**：§5.3 接口限流策略（行 1918–1932）
- **严重程度**：轻微 — 实现者可自行设定合理默认值，但契约设计不完整
- **改进建议**：在各限流层级补充桶容量规格。建议如：家属角色速率 30 req/s、桶容量 45（= 速率的 1.5 倍，允许 1.5s 短时突发）；全局 1000 req/s、桶容量 1500

---

### 问题 7（轻微·错误响应边界不一致）：S2 `404` 语义与其他服务不一致

- **问题描述**：S2（§1.2）错误响应声明"指定的 `tripId` 不存在时返回空集合（`activeInterventions: []`），不视为错误"——即对不存在的资源返回 `200 OK` 而非 `404 Not Found`。但 S1（§1.1）明确列出 `404 — 驾驶员不存在`。S4 同样列出 `404 — 资源不存在（如指定的 fleetId 或 reportId 不存在）`。S2 的 404 语义作为例外，API 消费者需记住"查询干预状态用 200+空集合判断资源是否存在，其他查询用 404 判断"，增加误用风险。若此决策有设计理由（如避免向调用方暴露资源存在性），应在文档中明确说明。
- **所在位置**：§1.2 错误响应（行 117–119），对比 §1.1 错误响应（行 70）、§1.4 错误响应（行 489）
- **严重程度**：轻微 — 不影响安全性，但降低 API 一致性体验
- **改进建议**：二选一：(a) 统一为 `404`，与其他服务一致；(b) 在 S2 错误响应中添加设计理由说明（如"干预状态查询为安全非敏感操作，避免 404 向调用方泄露行程 ID 的存在性信息"）

---

## 二、整体质量评估

### 通过项
- 六个应用服务的 REST 端点覆盖满足需求要求（所有需求列示的功能均有对应端点）
- MQTT 主题路由表完整覆盖设备-云通信和推送场景，Payload JSON Schema 覆盖核心和次要 Topic
- 跨文档 DTO 不一致问题已正确标注为"保留（根源在应用层 OOD）"，无事实错误
- S3→S5 手动救援流转的 `createRescueReport` 方法已落地（v10），文档交叉引用正确
- 家属权限查询仅提供 GET 端点的设计理由已充分阐述
- v11 新增的登录端点、GPS 命名统一、豁免触发条件修正均正确执行
- 文档结构清晰，各修订说明块覆盖完整（v2→v11），可追溯性良好

### 整体评价
产出已达到 API 接口契约级设计的可交付水平——核心 API 路径、MQTT 路由和推送通道、前端对接模型、安全设计均有系统化定义。上述 7 个问题主要集中在**认证链路完整性**（refresh token 端点缺失、错误响应体结构缺失）和**边界一致性**（错误码覆盖不完整、S2 404 语义差异化）两个维度，不涉及事实性错误或需求响应缺失。建议优先修复问题 1（refresh 端点）和问题 2（S5/S6 错误响应补全），其余问题可在后续迭代中逐步完善。
