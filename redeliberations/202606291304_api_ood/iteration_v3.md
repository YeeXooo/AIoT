# 再审议判定报告（v3）

## 判定结果

RETRY

## 判定理由

质询报告结论为 LOCATED，实际轮次 1 < 最大轮次 12（提前终止，审查被确认），所有 8 个问题均经质询验证通过，审查结论可信。

诊断报告包含 **2 个严重**问题（问题 1：S3→S5 救援 ID 体系断裂；问题 2：新增 API 端点对应应用层方法未定义，违反需求约束 §49）和 **4 个一般等级**问题（问题 3：`familyAccountId` 安全校验缺失；问题 4：MQTT Topic 定义缺失；问题 5：`dataConsistency` 字段未定义；问题 6：前端调用列表遗漏端点），以及 2 个轻微问题。

根据判定标准，审查报告包含严重等级问题，达到 RETRY 条件。

## 需要解决的问题

- **问题描述**：S3 手动救援触发返回 `rescueRequestId`，S5 救援历史等所有端点使用 `rescueReportId`，未定义两者关联关系及跨服务流转逻辑
- **所在位置**：§1.3 TriggerManualRescueResponse（:195）、§1.5 各端点（:459,:468,:516）、§3.1 WebSocket 下行（:1293）
- **严重程度**：严重
- **改进建议**：在 TriggerManualRescueResponse 中补充 `rescueReportId` 字段，或添加说明块阐明 S3 手动救援触发后如何在 S5 创建救援记录并获取 `rescueReportId` 的流转逻辑

- **问题描述**：3 个新增端点（`queryGuardianshipPermissions`、`issueSparkRTCToken`、`cancelUpgradeTask`）仅有"预签名"注释，应用层 OOD（`docs/ood_application.md`）中不存在对应方法定义，违反需求约束 §49
- **所在位置**：§1.3（:132-133）、§1.6（:550），对比 `docs/ood_application.md` §3.3（:260-271）、§3.6（:451-456）
- **严重程度**：严重
- **改进建议**：在 `docs/ood_application.md` 中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法签名及 DTO，为 S6 补充 `cancelUpgradeTask` 方法签名及 DTO

- **问题描述**：多个 S3 端点请求体包含 `familyAccountId` 字段，但未校验其与 JWT `sub` 一致，存在横向越权风险
- **所在位置**：§1.3 各请求体（:141-211）、§5.1 JWT Payload（:1652）、§5.6 隐私校验表（:1773）
- **严重程度**：一般
- **改进建议**：补充安全门控规则——校验 `familyAccountId` 与 JWT `sub` 一致，不一致则拒绝请求；或从请求体移除该字段改为从 JWT 隐式提取

- **问题描述**：MQTT Topic `cmd/media/join/down` 在主题路由总表和 Payload 定义中均缺失，流程图（:1325）为其唯一引用
- **所在位置**：§2.1 主题路由总表（:666-692）、§2.2 Payload 定义（:693-1245）
- **严重程度**：一般
- **改进建议**：在 §2.1 路由表新增该主题行，在 §2.2 补充 Payload 定义（至少含 `sparkRTCRoomId`、`sparkRTCJoinToken`、`commandId`）

- **问题描述**：§1.4 QueryTrajectoryResponse 端点描述提及 `dataConsistency` 字段但响应 JOSN 示例中未包含，且枚举值未定义
- **所在位置**：§1.4 端点描述（:371）与 JSON 示例（:362-369）
- **严重程度**：一般
- **改进建议**：在 QueryTrajectoryResponse 中补充 `dataConsistency` 字段（枚举 `CONSISTENT | INCONSISTENT`），并在 JSON 示例中体现

- **问题描述**：§4.1 家属 APP REST API 调用列表遗漏 `requestMediaSession` 端点，对应 ArkTS DTO 类型也未定义
- **所在位置**：§1.3（:126）、§4.1 REST API 调用列表（:1390-1398）、ArkTS DTO 定义（:1402-1536）
- **严重程度**：一般
- **改进建议**：在 §4.1 中明确调用方式并补充对应 ArkTS 数据模型
