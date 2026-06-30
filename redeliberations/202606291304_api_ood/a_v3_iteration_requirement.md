根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

本轮审查发现 8 个问题（2 严重 / 2 中等 / 4 一般/轻微），全部经质询确认为 LOCATED：

1. **严重 — 新增 API 端点未映射到应用层方法**：v2 新增的 3 个 REST 端点（`GET /api/v1/guardianship/{driverId}/permissions`、`POST /api/v1/sparkrtc/token`、`DELETE /api/v1/ota/upgrade-tasks/{taskId}`）在应用层 OOD（`docs/ood_application.md`）中无对应的方法签名，违反需求 §2 约束"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"。建议在应用层 OOD 中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法，为 S6 补充 `cancelUpgradeTask` 方法；或在 API OOD 中明确标注这三个端点对应应用层待新增方法的具体预签名（方法名 + 输入/输出 DTO 类型）。

2. **中等 — 家属手动救援触发端点归属与需求分组不一致**：需求 `requirement.md:27` 将"家属手动救援触发"列入 S5 EmergencyRescueService，但本文档 §1.3 将该端点（`POST /api/v1/guardianship/manual-rescue`）归入 S3 RemoteGuardianshipService。建议方案 A（推荐）：在文档开篇或 S3/S5 分节处添加说明，解释设计层面基于职责内聚的归口调整；或方案 B：将端点从 §1.3 S3 移至 §1.5 S5。

3. **中等 — S5 queryRescueHistory 端点认证标注与 §5.1 角色映射矛盾**：§1.5 标注 `JWT`（无角色限定），但 §5.1 角色→权限映射表明确限定 S5 全部端点仅 `RESCUE` 角色可访问。建议将 §1.5 中 `queryRescueHistory` 端点的认证列统一标注为 `JWT (RESCUE)`。

4. **一般 — SparkRTC Token 独立端点的消费者未阐明**：`POST /api/v1/sparkrtc/token` 端点未说明目标调用方，§4.1 家属 APP REST 调用列表和 §4.3 HMI 本地查询接口表中均未列出。建议补充调用场景说明，若面向家属 APP 应在 §4.1 中补入。

5. **一般 — TriggerRollbackResponse 示例未覆盖 ROLLING_BACK 中间状态**：JSON 示例仅展示 `ROLLED_BACK`，缺失 `ROLLING_BACK` 状态。建议补充 `newStatus` 的可能取值（`ROLLING_BACK` / `ROLLED_BACK`）及两种状态的含义说明。

6. **一般 — S4 report download 端点响应未指定 Content-Type**：不同 `format` 参数应返回不同 Content-Type。建议补充 Content-Type 说明（`format=pdf → application/pdf`，`format=xlsx → application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`）及 `Content-Disposition` 头。

7. **轻微 — MQTT 主题模板语法不一致**：§2.1 主题路由总表中 `${sensorType}` 使用 `$` 前缀，而其余模板均使用 `{variable}` 语法。建议统一为 `{sensorType}`。

8. **轻微 — DELETE 请求的成功响应码约定不统一**：S3 的 `DELETE /media-session/{sessionHandle}` 返回 `204 No Content`，S6 的 `DELETE /upgrade-tasks/{taskId}` 返回 `200 OK` 并携带 `CancelUpgradeTaskResponse` 响应体。建议在文档开篇统一 DELETE 响应码策略。

## 历史迭代回顾

### 已解决的问题
- **第 1 轮问题 2 — MQTT Payload JSON Schema 覆盖不完整**：v2 已补充 14 个 Payload 定义（车窗控制指令、车门解锁指令、OTA 指令、指令 Ack、传感器故障、摄像头遮挡、心跳、行程状态、生理体征快照、车辆状态遥测、驾驶员覆盖信号、行程评分、路怒语音存证及 6 个推送消息），当前反馈中不再提及，确认已解决。

### 持续存在的问题
- **第 1 轮问题 1 → 第 2 轮问题 1 — 端点映射到应用层方法**：第 1 轮建议新增 S3 家属权限查询端点，v2 新增了端点但未在应用层 OOD 中补充对应方法签名，导致本轮问题 1（严重的跨层契约断层）。此问题的本质是 v2 修订不完整，需在本轮彻底解决——在文档中明确标注三个新增端点对应的应用层方法预签名。

### 新发现的问题（本轮新识别）
- 问题 2（家属手动救援触发端点归属不一致）
- 问题 3（S5 queryRescueHistory 认证标注矛盾）
- 问题 4（SparkRTC Token 端点消费者未阐明）
- 问题 5（TriggerRollbackResponse 缺失 ROLLING_BACK 状态）
- 问题 6（report download 未指定 Content-Type）
- 问题 7（MQTT 主题模板语法不一致）
- 问题 8（DELETE 响应码约定不统一）

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v2_copy_from_v1.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
