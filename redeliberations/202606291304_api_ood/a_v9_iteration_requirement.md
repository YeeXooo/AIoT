根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

本轮诊断报告（b_v8_diag_v3.md）经质询确认（LOCATED），识别出 6 个 API OOD 自身质量问题：

### 问题 1（严重 · Markdown 格式错误 · 新增）
§1.6 `TriggerRollbackResponse` 的 JSON 代码块之后存在多余的独立 ``` 行（第 646 行），该行不匹配任何代码块起始标记，导致 Markdown 解析器将后续所有 JSON 示例错误地渲染为代码块外的普通文本，破坏文档结构性。
- 所在位置：§1.6 第 646 行
- 改进建议：删除第 646 行的多余 ```。

### 问题 2（中等 · 版本标识与当前轮次矛盾 · 持续性）
文档标题为"a_v9 / v9"，但该文件实际为第 8 轮产出（文件名 a_v8_copy_from_v7.md）。当前迭代为第 9 轮，应在标题中将版本号修正为与文件名前缀一致的 v8（反映该版实际创生轮次），或修正为 v9 并统一文件名与标题。推荐将标题修正为正确的轮次标识。
- 所在位置：第 1 行（标题）、第 2017 行（"修订说明（v9）"块）
- 改进建议：修正标题和修订说明块的版本号，使其与文件名（a_v8）和实际轮次一致；或在复制为 a_v9 后将文件内所有版本号统一更新为 v9。

### 问题 3（中等 · REST API 契约存在设计死值 · 持续性）
`POST /api/v1/sparkrtc/token` 端点的 `IssueSparkRTCTokenRequest.role` 和 ArkTS `SparkRTCRole` 类型包含 `publisher` 取值。但 §1.3 安全约束块和 §5.1 角色映射明确规定 FAMILY 仅可请求 `subscriber`，且当前角色体系下不存在任何角色能通过 REST 端点合法请求 `publisher`。保留此值增加前端误用风险。
- 所在位置：§1.3 行 304、§4.1 ArkTS `SparkRTCRole` 类型别名（行 1514）、§1.3 安全约束块（行 306）
- 改进建议：将 REST 端点和 ArkTS 类型的 `role` 取值限定为 `'subscriber'`，移除 `publisher`。或在 API 契约层面显式标注"FAMILY 角色请求此端点时仅可使用 subscriber"（当前已有安全约束注解，推荐补充明确的天花板限制说明）。

### 问题 4（中等 · 请求体示例缺失 · 新增）
§1.4 端点表定义了 `POST /api/v1/fleet/performance-warning-subscription` 端点，标注请求体为 `SubscribePerformanceWarningRequest`，但该节仅给出了 `SubscribePerformanceWarningResponse` 的 JSON 示例，未提供 Request 的 JSON 示例。
- 所在位置：§1.4 行 340 引用 `SubscribePerformanceWarningRequest`，缺少对应 Request JSON 示例
- 改进建议：在 §1.4 补充 `SubscribePerformanceWarningRequest` 的 JSON 示例（含 `adminId` 和 `fleetId` 字段）。

### 问题 5（一般 · WebSocket 消息枚举值缺失 · 新增）
§3.1 WebSocket 下行消息 `rescue_triggered` 的 Payload 中 `status` 字段未枚举可能取值，下游实现者需从上下文推断。
- 所在位置：§3.1 行 1372
- 改进建议：将 `"status": "..."` 改为 `"status": "PENDING | CONFIRMED | REJECTED"`，并添加注释"与 §1.3 TriggerManualRescueResponse.status 语义一致"。

### 问题 6（轻微 · S1/S5 错误响应 401 缺失 · 持续性）
S1（§1.1）和 S5（§1.5）的错误响应列表中未包含 `401 Unauthorized` 状态码，而 S4 等节显式列出了 401。
- 所在位置：§1.1 行 66–69、§1.5 行 562–564
- 改进建议：统一策略——(a) 所有端点错误响应中一致列出 401；(b) 在 §一 总述段落后统一声明"401 由 API 网关统一处理，各端点不单独标注"。推荐方案 (b) 更简洁。

### 跨层一致性提醒
诊断报告 §四 列出 8 项跨层不一致（C1–C8），均非 API OOD 自身缺陷。其中 C1（`ControlVehicleWindowRequest` 缺少 `windowPosition` 字段）为新增发现且未被产出作者标注处理，其余 7 项已被作者标注"保留/以领域层为准/已标注"。本条为信息性提醒，不要求修改 API OOD。

## 历史迭代回顾

### 已解决的问题（出现在历史反馈但当前反馈中不再提及）
- 轮次 7 问题 2（§5.1 缺少 OAuth2 排除理由）→ 已在 v8 §5.1 开篇补充完整设计决策说明
- 轮次 7 问题 3（`POST /api/v1/auth/secondary-verify` 端点缺失）→ 已在 v8 §1.7 正式定义
- 轮次 7 问题 4（13 个 ArkTS 枚举/字面量类型仅行内注释）→ 已在 v8 §4.1 新增 15 个独立 `type` 别名声明
- 轮次 5 问题 5（S5/S6 端点表缺查询参数列）、问题 6（S2 缺失错误响应）→ 已在 v7/v8 修复
- 轮次 2–轮次 7 多项跨层 DTO 不一致、MQTT 语法统一、DELETE 响应码约定等 → 均已处理

### 持续存在的问题（在多轮反馈中反复出现，需重点解决）
1. **版本标识不一致**：轮次 7 出现"文件名 v7/标题 v8/轮次 7"矛盾，轮次 8 产出中再次出现"文件名 v8/标题 v9/轮次 8"矛盾。v8 的修订说明（v9）声称"将标题更新为 a_v9 / v9 与当前迭代轮次 v9 一致"构成事实错误——当时实际轮次为 8。当前进入轮次 9，请确保版本标识与文件名、轮次三者一致。
2. **SparkRTCRole publisher 作为设计死值**：轮次 6 已为此端点添加安全约束注解，但 `publisher` 值在多轮审查中始终被标记为设计死值（REST 端点无合法调用方）。质询报告建议审慎评估此为"设计取舍"而非"设计缺陷"——建议明确文档策略并标注天花板限制。
3. **S3→S5 createRescueReport 接口契约待补**：轮次 3 至今持续标注，问题根源在应用层 OOD（`docs/ood_application.md`）接口方法表缺失该方法，API OOD 层面注解块已充分。

### 新发现的问题（本轮新识别）
- 问题 1：Markdown 格式错误（多余 ```，严重）
- 问题 4：SubscribePerformanceWarningRequest JSON 示例缺失（中等）
- 问题 5：WebSocket rescue_triggered status 枚举值缺失（一般）

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v8_copy_from_v7.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
