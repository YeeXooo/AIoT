# 再审议判定报告（v8）

## 判定结果

RETRY

## 判定理由

诊断报告（b_v8_diag_v3.md）识别出 6 个问题，包含 1 个严重问题（Markdown 格式错误导致后续所有 JSON 示例渲染异常）、3 个中等问题（版本标识矛盾、publisher 死值、请求体示例缺失）、1 个一般问题（WebSocket 枚举值缺失）及 1 个轻微问题（S1/S5 401 缺失）。质询报告（b_v8_challenge_v3.md）结论为 LOCATED，确认了诊断报告中问题的有效性。实际轮次 3 < 最大轮次 12，质询已提前终止且确认问题存在。因诊断报告含有严重和一般等级的问题，根据判定标准应判为 RETRY。

## 需要解决的问题

- **问题描述**：§1.6 `TriggerRollbackResponse` 的 JSON 代码块之后存在多余 ` ``` ` 独立行（第 646 行），导致 Markdown 解析器将后续所有内容错误渲染为代码块外普通文本，影响所有后续 JSON 示例的结构可读性
- **所在位置**：产出文档 §1.6 第 646 行
- **严重程度**：严重
- **改进建议**：删除第 646 行的多余 ` ``` `

- **问题描述**：文档标题为"a_v9 / v9"，但当前实际迭代轮次为第 8 轮，修订说明块声称"与当前迭代轮次（v9）一致"构成事实错误
- **所在位置**：产出文档第 1 行（标题）及第 2017 行（修订说明块）
- **严重程度**：中等
- **改进建议**：将标题从"a_v9 / v9"修正为"a_v8 / v8"，将"修订说明（v9）"更新为"修订说明（v8）"

- **问题描述**：`POST /api/v1/sparkrtc/token` 端点的 `role` 取值包含 `publisher`，但当前角色体系下无任何角色能合法通过此 REST 端点请求该值，保留此值增加误用风险
- **所在位置**：§1.3 `IssueSparkRTCTokenRequest.role`（行 304）、§4.1 ArkTS `SparkRTCRole`（行 1514）、§1.3 安全约束块（行 306）
- **严重程度**：中等
- **改进建议**：将 REST 端点和 ArkTS 类型的 `role` 取值限定为 `'subscriber'`，移除 `publisher`；或在 API 契约层面显式标注"FAMILY 角色请求此端点时仅可使用 subscriber"

- **问题描述**：`POST /api/v1/fleet/performance-warning-subscription` 端点仅给出了 `SubscribePerformanceWarningResponse` 的 JSON 示例，未提供 `SubscribePerformanceWarningRequest` 的 JSON 示例，API 使用者无法直接获知请求字段
- **所在位置**：§1.4 端点表行 340 及缺少对应 Request JSON 示例处
- **严重程度**：中等
- **改进建议**：补充 `SubscribePerformanceWarningRequest` 的 JSON 示例（字段为 `adminId`、`fleetId`）

- **问题描述**：§3.1 WebSocket 下行消息 `rescue_triggered` 的 Payload 中 `status` 字段未枚举可能取值，下游实现者需从上下文推断
- **所在位置**：§3.1 WebSocket 下行消息表 `rescue_triggered` 行（行 1372）
- **严重程度**：一般
- **改进建议**：将 `"status": "..."` 改为 `"status": "PENDING | CONFIRMED | REJECTED"`，并添加交叉引用

- **问题描述**：S1（§1.1）和 S5（§1.5）的错误响应列表中未包含 `401 Unauthorized` 状态码，与其他服务（如 S4）不一致
- **所在位置**：§1.1 错误响应（行 66–69）、§1.5 错误响应（行 562–564）
- **严重程度**：轻微
- **改进建议**：统一策略：在所有认证端点一致列出 401，或在总述段落后统一声明"401 由 API 网关统一处理，各端点不单独标注"
