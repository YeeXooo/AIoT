根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

本轮审查（b_v3_diag_v1）发现 8 个问题，质询确认全部 **LOCATED**：

**严重问题：**

1. **S3→S5 救援记录的 ID 体系断裂**：S3 手动救援触发（`POST /api/v1/guardianship/manual-rescue`）返回 `rescueRequestId`，WebSocket 推送同样使用 `rescueRequestId`；但 S5 所有端点均使用 `rescueReportId`。未定义 `rescueRequestId` 如何关联到 `rescueReportId`，也未描述 S3 触发后如何编排流转至 S5 的救援记录。所在位置：§1.3 TriggerManualRescueResponse（:195）、§1.5 各端点（:459,:468,:516）、§3.1 WebSocket 下行 rescue_triggered（:1293）、§2.2 SOS 确认通知表格（:1241）。改进建议：在 TriggerManualRescueResponse 中补充 `rescueReportId` 字段，或添加说明块阐明 S3 手动救援触发后如何在 S5 创建救援记录并获取 `rescueReportId` 的流转逻辑。

2. **新增 API 端点在应用层 OOD 中仍无实际方法定义**：需求约束 §49 要求"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"。产出在 v3 中为 3 个新增端点添加了"预签名"注释，但经核查 `docs/ood_application.md`，这些方法在实际文件中并不存在：`IRemoteGuardianshipService.queryGuardianshipPermissions`、`IRemoteGuardianshipService.issueSparkRTCToken`、`IOTAManagementService.cancelUpgradeTask`。"预签名"仅是一份承诺性标注，不等同于应用层 OOD 已完成对应方法定义。所在位置：§1.3（:132-133）、§1.6（:550），对比 `docs/ood_application.md` §3.3（:260-271）、§3.6（:451-456）。改进建议：在 `docs/ood_application.md` 中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法签名及 DTO，为 S6 补充 `cancelUpgradeTask` 方法签名及 DTO；同时将 API OOD 中的"预签名"标注更新为对实际应用层方法签名的映射引用。

**中等问题：**

3. **S3 请求体中的 `familyAccountId` 可被客户端篡改**：多个 S3 端点请求体均包含 `familyAccountId` 字段，但该值应从 JWT Token 的 `sub` 声明中提取。JWT Payload 中已包含 `sub`（账户 ID），API 层应从中提取身份而非信任请求体。当前设计存在横向越权风险——恶意客户端可传入其他账户的 ID。对比 §5.6 中家属权限查询入口明确校验了"仅返回与请求方 accountId 关联的监护权限"（:1773），但上述写操作的请求体校验未作同样声明。所在位置：§1.3 各请求体（:141-211）、§5.1 JWT Payload（:1652）、§5.6 隐私校验表（:1773）。改进建议：在 API 安全门控说明中补充规则——所有包含 `familyAccountId` 的请求体，应用层入口须校验其与 JWT `sub` 一致，不一致则拒绝请求并记录安全审计日志。或直接从 API 契约中移除 `familyAccountId` 字段，改为从 JWT 中隐式提取。

4. **MQTT Topic `cmd/media/join/down` 定义缺失**：§3.2 SparkRTC 房间管理流程图（:1325）引用了 `cmd/media/join/down` 主题用于云端向车机下发 SparkRTC 入房凭证，但该主题在 §2.1 MQTT 主题路由总表中未被列出，且 §2.2 未提供其 Payload JSON Schema 或字段级定义。所在位置：§2.1 主题路由总表（:666-692）— 缺少 `cmd/media/join/down`、§2.2 Payload 定义（:693-1245）— 无对应 Schema 或表格、§3.2 流程图（:1325）— 唯一引用处。改进建议：在 §2.1 路由表中新增一行，在 §2.2 中补充其 Payload 定义（至少包含 `sparkRTCRoomId`、`sparkRTCJoinToken`、`commandId`）。

**一般问题：**

5. **S4 轨迹查询的 `dataConsistency` 字段未在响应模型中定义**：§1.4 端点描述注明当 `vehicleId` 和 `driverId` 同时提供但不匹配时返回空序列并标注 `dataConsistency = INCONSISTENT`（:371），但 QueryTrajectoryResponse 的 JSON 示例（:362-369）中不含此字段，枚举值也未定义。所在位置：§1.4 端点描述（:371）与 JSON 示例（:362-369）。改进建议：在 QueryTrajectoryResponse 模型中补充 `dataConsistency` 字段（类型 `string`，枚举 `CONSISTENT | INCONSISTENT`），并在 JSON 示例中体现。

6. **§4.1 家属 APP REST API 调用列表遗漏 `requestMediaSession` 端点**：§1.3 定义了 `POST /api/v1/guardianship/media-session` 端点，但 §4.1 家属 APP REST API 调用列表中未包含此端点。当前设计存在歧义：§3.1 WebSocket 协议中提供了 `request_media` 上行消息，暗示家属 APP 通过 WebSocket 建立对讲；但 REST API 契约 §1.3 也定义了同名端点。ArkTS 开发者无法从 §4.1 确定应通过 REST 还是 WebSocket 调用此功能，亦无法获取对应的 ArkTS 数据类型定义。所在位置：§1.3 REST 端点表（:126）、§4.1 REST API 调用列表（:1390-1398）、§4.1 ArkTS DTO 定义（:1402-1536）。改进建议：在 §4.1 中明确说明家属 APP 通过 WebSocket（`request_media` 消息）还是 REST（`POST /media-session`）调用对讲建立，并补充对应的 ArkTS 数据模型。

**轻微问题：**

7. **S6 cancelUpgradeTask 端点行表格列数异常**：§1.6 端点表统一为 7 列，但 cancelUpgradeTask 行在 `DELETE` 方法和路径之后多了一个单独的 `—` 单元格，使其成为 8 列行，将导致 Markdown 表格渲染时列错位。所在位置：§1.6 端点表（:550）。改进建议：删除多余单元格使列数恢复为 7 列。

8. **ArkTS DTO 类型名与 REST API 定义命名不一致**：§4.1 ArkTS 数据模型使用缩写命名（如 `UpdateNotificationPreferenceReq`、`TriggerManualRescueReq`），而 §1 REST API 契约使用全名（`UpdateNotificationPreferenceRequest`、`TriggerManualRescueRequest`）。命名不一致可能导致跨团队协作时的混淆。所在位置：§1.3 Request/Response 定义（:141-293）、§4.1 ArkTS interface 定义（:1402-1536）。改进建议：在 §4.1 开篇添加说明行声明命名约定（Req=Request, Resp=Response），或统一两侧使用全名。

## 历史迭代回顾

**已解决的问题**（历史反馈中出现但当前不再提及）：
- 第 1 轮问题 1（S3 缺失家属权限查询端点）→ 已补充
- 第 1 轮问题 2（MQTT Payload 覆盖不完整）→ 已大幅补充，仅残留问题 4
- 第 2 轮问题 2（手动救援端点归属与需求分组不一致）→ v3 已添加设计说明
- 第 2 轮问题 3（S5 queryRescueHistory JWT 认证标注不一致）→ 已修复
- 第 2 轮问题 5（TriggerRollbackResponse 缺少 ROLLING_BACK 状态）→ 已补充
- 第 2 轮问题 6（S4 report download 缺少 Content-Type）→ 已补充
- 第 2 轮问题 7（MQTT 主题模板语法不一致）→ 已统一为 `{variable}`
- 第 2 轮问题 8（DELETE 响应码约定不统一）→ v3 已添加统一说明

**持续性存在的问题**（多轮反馈中反复出现，需重点解决）：
- **新增端点未映射到应用层方法**：第 2 轮问题 1 → 第 3 轮问题 2 → 当前问题 2。v2 添加"预签名"处理后，v3 仍仅以"预签名"承诺性标注形式存在，实际应用层方法未落地。此问题已持续 3 轮未根本解决。
- **S3→S5 救援 ID 体系断裂**：第 3 轮问题 1 → 当前问题 1，v3 未处理。
- **familyAccountId 安全缺陷**：第 3 轮问题 3 → 当前问题 3，v3 未处理。
- **MQTT cmd/media/join/down 缺失**：第 3 轮问题 4 → 当前问题 4。v3 内部审议已标记但未在产出中修复。
- **dataConsistency 字段缺失**：第 3 轮问题 5 → 当前问题 5。
- **requestMediaSession 前端指引不完整**：第 3 轮问题 6 → 当前问题 6。

**新发现的问题**（本轮首次识别）：
- 问题 7（S6 cancelUpgradeTask 表格格式错误）
- 问题 8（ArkTS DTO 命名不一致）

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v3_copy_from_v2.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
