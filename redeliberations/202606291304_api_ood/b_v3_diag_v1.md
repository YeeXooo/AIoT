# API/接口层 OOD 质量审查报告（v3 / b_v3_diag_v1）

本报告对产出 `a_v3_copy_from_v2.md` 进行质量审查，侧重需求响应充分度、事实正确性、逻辑一致性及深度完整性，不重复验证内部审议已覆盖的技术可行性等维度。

---

## 一、问题清单

### 问题 1（严重）：S3 手动救援 → S5 救援记录的 ID 体系断裂

**问题描述**：S3 手动救援触发（`POST /api/v1/guardianship/manual-rescue`）返回 `rescueRequestId`，WebSocket 推送同样使用 `rescueRequestId`；但 S5 的救援历史查询（`queryRescueHistory`）及 SOS 确认等所有端点均使用 `rescueReportId`。S5 的 `triggerType` 枚举包含 `MANUAL`，表明家属手动救援应记录于 S5 救援历史，但文档未定义 `rescueRequestId` 如何关联到 `rescueReportId`，也未描述 S3 触发后如何编排流转至 S5 的救援记录。实现人员无法据此完成跨服务的救援链路设计。

**所在位置**：
- §1.3 TriggerManualRescueResponse（:195）：使用 `rescueRequestId`
- §1.5 ConfirmSOSReportRequest（:459）、IssueRescueTokenRequest（:468）、QueryRescueHistoryResponse（:516）：使用 `rescueReportId`
- §3.1 WebSocket 下行 `rescue_triggered` 消息（:1293）：使用 `rescueRequestId`
- §2.2 末 SOS 确认通知表格（:1241）：使用 `rescueReportId`

**严重程度**：严重 — 跨服务编排链路不完整，影响实现。

**改进建议**：在 TriggerManualRescueResponse 中补充 `rescueReportId` 字段，或添加说明块阐明 S3 手动救援触发后如何在 S5 创建救援记录并获取 `rescueReportId` 的流转逻辑。

---

### 问题 2（严重）：新增 API 端点在应用层 OOD 中仍无实际方法定义

**问题描述**：需求约束明确要求"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"（requirement.md:49）。产出在 v3 修订中为 3 个新增端点添加了"预签名"注释（§1.3:137-139、§1.6:650-652），但经核查 `docs/ood_application.md`，这些方法在实际文件中并不存在：

- `IRemoteGuardianshipService.queryGuardianshipPermissions(driverId, accountId)` — 应用层 §3.3 方法表（:260-271）中无此条目
- `IRemoteGuardianshipService.issueSparkRTCToken(request)` — 同上，不存在
- `IOTAManagementService.cancelUpgradeTask(taskId)` — 应用层 §3.6 方法表（:451-456）中无此条目

"预签名"仅是一份承诺性标注，不等同于应用层 OOD 已完成对应方法定义。下游接口层实现者若仅阅读应用层 OOD 契约，无法找到这三个端点的对应方法签名。

**所在位置**：
- §1.3 新增端点 `GET /api/v1/guardianship/{driverId}/permissions`（:132）、`POST /api/v1/sparkrtc/token`（:133）
- §1.6 新增端点 `DELETE /api/v1/ota/upgrade-tasks/{taskId}`（:550）
- 对比 `docs/ood_application.md` §3.3（:260-271）、§3.6（:451-456）

**严重程度**：严重 — 违反需求约束 §49，实现阶段将面临契约缺失。

**改进建议**：在 `docs/ood_application.md` 中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法签名及 DTO，为 S6 补充 `cancelUpgradeTask` 方法签名及 DTO；同时将 API OOD 中的"预签名"标注更新为对实际应用层方法签名的映射引用。

---

### 问题 3（中等）：S3 请求体中的 `familyAccountId` 可被客户端篡改

**问题描述**：以下 S3 端点请求体均包含 `familyAccountId` 字段，但该值应从 JWT Token 的 `sub` 声明中提取，而非信任客户端传入：

- `RequestMediaSessionRequest.familyAccountId`（:145）
- `UpdateNotificationPreferenceRequest.familyAccountId`（:173）
- `TriggerManualRescueRequest.familyAccountId`（:185）
- `ControlVehicleWindowRequest.familyAccountId`（:206）

JWT Payload 中已包含 `sub`（账户 ID），API 层应从中提取身份而非信任请求体。当前设计存在横向越权风险——恶意客户端可传入其他账户的 ID 以修改他人通知偏好或在他人的监护关系下发起操作。

对比 §5.6 中家属权限查询入口明确校验了"仅返回与请求方 accountId 关联的监护权限"(:1773)，但上述写操作的请求体校验未作同样声明。

**所在位置**：
- §1.3 各请求体（:141-211）
- §5.1 JWT Payload（:1652）中 `sub` 字段定义
- §5.6 隐私校验表（:1773）

**严重程度**：中等 — 安全设计缺陷，可能被利用但限于认证用户的越权操作。

**改进建议**：在 API 安全门控说明中补充规则——所有包含 `familyAccountId` 的请求体，应用层入口须校验其与 JWT `sub` 一致，不一致则拒绝请求并记录安全审计日志。或直接从 API 契约中移除 `familyAccountId` 字段，改为从 JWT 中隐式提取。

---

### 问题 4（中等）：MQTT Topic `cmd/media/join/down` 定义缺失

**问题描述**：§3.2 SparkRTC 房间管理流程图（:1325）引用了 `cmd/media/join/down` 主题用于云端向车机下发 SparkRTC 入房凭证（roomId + joinToken），但该主题在 §2.1 MQTT 主题路由总表中未被列出，且 §2.2 未提供其 Payload JSON Schema 或字段级定义。实现人员需自行推断该主题的 QoS 等级和 Payload 结构（含 `sparkRTCRoomId` 和 `sparkRTCJoinToken` 两个关键字段）。

此问题已在内部审议（a_v3_review_v1.md:36）中标记但未在 v3 产出中修复。

**所在位置**：
- §2.1 主题路由总表（:666-692）— 缺少 `cmd/media/join/down`
- §2.2 Payload 定义（:693-1245）— 无对应 Schema 或表格
- §3.2 流程图（:1325）— 唯一引用处

**严重程度**：中等 — 关键信令链路的 Topic 和 Payload 未定义。

**改进建议**：在 §2.1 路由表中新增一行 `下指令 | {deviceId}/cmd/media/join/down | 1 | 媒体加入指令 | 下发 SparkRTC 入房凭证`；在 §2.2 中补充其 Payload 定义（至少包含 `sparkRTCRoomId`、`sparkRTCJoinToken`、`commandId`）。

---

### 问题 5（一般）：S4 轨迹查询的 `dataConsistency` 字段未在响应模型中定义

**问题描述**：§1.4 QueryTrajectoryResponse 端点描述注明：当 `vehicleId` 和 `driverId` 同时提供但不匹配时，返回空序列并标注 `dataConsistency = INCONSISTENT`（:371）。但该响应体 JSON 示例（:362-369）中不含 `dataConsistency` 字段。文档对包含此字段时的取值枚举（如 `CONSISTENT` / `INCONSISTENT` / 其他）也未定义。

若前端需根据此字段做 UI 提示（如"查询参数矛盾，请检查"），则字段缺失会导致该逻辑无法落地。

**所在位置**：
- §1.4 端点描述（:371）：提及 `dataConsistency = INCONSISTENT`
- §1.4 QueryTrajectoryResponse JSON 示例（:362-369）：无 `dataConsistency` 字段

**严重程度**：一般 — 响应模型与端点语义描述不一致。

**改进建议**：在 QueryTrajectoryResponse 模型中补充 `dataConsistency` 字段（类型 `string`，枚举 `CONSISTENT | INCONSISTENT`），并在 JSON 示例中体现。

---

### 问题 6（一般）：§4.1 家属 APP REST API 调用列表遗漏 `requestMediaSession` 端点

**问题描述**：§1.3 定义了 `POST /api/v1/guardianship/media-session` 端点用于请求建立音视频对讲，但 §4.1 家属 APP（HarmonyOS）REST API 调用列表中未包含此端点。该列表仅含 7 个接口，不包括 media-session 的创建和终止。

当前设计存在歧义：§3.1 WebSocket 协议中提供了 `request_media` 上行消息，暗示家属 APP 通过 WebSocket 建立对讲；但 REST API 契约 §1.3 也定义了同名端点。ArkTS 开发者无法从 §4.1 确定应通过 REST 还是 WebSocket 调用此功能，亦无法获取对应的 ArkTS 数据类型定义（RequestMediaSessionRequest/Response 未在 §4.1 DTO 区域给出 TypeScript 接口）。

**所在位置**：
- §1.3 REST 端点表（:126）：`POST /api/v1/guardianship/media-session`
- §4.1 REST API 调用列表（:1390-1398）：缺失此行
- §4.1 ArkTS DTO 定义（:1402-1536）：无 `RequestMediaSessionRequest`/`Response` 等类型

**严重程度**：一般 — 前端开发者调用指引不完整。

**改进建议**：在 §4.1 中明确说明家属 APP 通过 WebSocket（`request_media` 消息）还是 REST（`POST /media-session`）调用对讲建立，并补充对应的 ArkTS 数据模型。如两者均可用，应分别列出并说明适用场景。

---

### 问题 7（轻微）：S6 cancelUpgradeTask 端点行表格列数异常

**问题描述**：§1.6 端点表统一为 7 列（`端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证`）。但第 550 行 `cancelUpgradeTask` 端点在 `DELETE` 方法和路径之后多了一个单独的 `—` 单元格，使其成为 8 列行（实际内容：`| **取消升级任务** | DELETE | .... | — | — | CancelUpgradeTaskResponse | 200 | JWT |`）。对比同表的其他 DELETE 类端点（如 S3 的 `DELETE /media-session/...` 行 :127，正确地在 `请求体` 和 `响应体` 列各有 `—`），本行多出的 `—` 将导致 Markdown 表格渲染时列错位。

此问题已在内部审议（a_v3_review_v1.md:33）中标记但未在 v3 产出中修复。

**所在位置**：§1.6 端点表（:550）

**严重程度**：轻微 — 不影响语义，但作为接口契约文档，表格格式错误可能导致工具解析失败。

**改进建议**：删除第 550 行中多余的 `—` 单元格。

---

### 问题 8（轻微）：ArkTS DTO 类型名与 REST API 定义命名不一致

**问题描述**：§4.1 ArkTS 数据模型使用缩写命名（如 `UpdateNotificationPreferenceReq`、`TriggerManualRescueReq`、`ControlVehicleWindowReq`、`QueryWindowStatusResp`），而 §1 REST API 契约使用全名（`UpdateNotificationPreferenceRequest`、`TriggerManualRescueRequest`、`ControlVehicleWindowRequest`、`QueryWindowStatusResponse`）。两个版本在概念上等价，但命名差异可能导致跨团队协作时的混淆——后端开发者参考 §1 使用全名，前端开发者参考 §4 使用缩写，双方需自行推断映射关系。

**所在位置**：
- §1.3 Request/Response 定义（:141-293）
- §4.1 ArkTS interface 定义（:1402-1536）

**严重程度**：轻微 — 不阻塞实现，但增加沟通成本。

**改进建议**：在 §4.1 开篇添加说明行，声明 ArkTS 接口命名约定（Req=Request, Resp=Response），或统一两侧使用全名。

---

## 二、综合评价

产出在整体上完成了需求要求的五部分内容（REST API 契约、MQTT 主题设计、WebSocket/SparkRTC 集成、ArkTS 前端对接、安全设计），六项应用服务的端点清单基本完整，JSON Schema 定义详实可读。

但产出在以下方面存在未解决的不足：

1. **跨服务链路不完整**：S3→S5 的救援流转缺少 ID 体系桥接，实现者无法直接使用。
2. **契约层间不一致**：3 个新增端点虽有"预签名"，但上游应用层 OOD 未同步更新，违反需求约束。
3. **安全细节疏漏**：多个 S3 端点的 `familyAccountId` 输入校验未明确，存在越权风险窗口。
4. **遗留已知缺陷未修复**：内部审议已标记的 MQTT topic 缺失和表格格式错误在 v3 中未被处理。

---

## 修订说明（v1）

首轮审查，无历史质询意见。
