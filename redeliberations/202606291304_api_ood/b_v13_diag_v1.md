# API/接口层 OOD 质量审查报告（v13）

> 审查轮次：首轮审查（v13）
> 审查范围：需求响应充分度、整体深度与完整性、事实错误与逻辑矛盾
> 待审查文件：`a_v13_copy_from_v12.md`
> 审查基准：`requirement.md`

---

## 审查总评

产出经 12 轮迭代已趋于成熟，五部分设计（REST API、MQTT、WebSocket/SparkRTC、ArkTS 对接、安全设计）均已覆盖需求要求。v13 修订块宣称的 8 个问题修复均已落实到位。当前剩余问题集中在局部字段定义不完整、跨节数据模型一致性欠佳等细节层面，无阻塞性缺陷。

---

## 发现的质量问题

### 问题 1：§3.1 WebSocket `access_granted` 下行消息 Payload 缺少 `reason` 字段

- **所在位置**：§3.1 下行消息表 `access_granted` 行（约行 1494）
- **严重程度**：一般
- **问题描述**：§3.1 中 `access_granted` 消息 Payload 定义为 `{ "driverId": "...", "sessionToken": "...", "sparkRTCRoomId": "...", "sparkRTCJoinToken": "..." }`，未包含 `reason` 字段。但存在以下三处矛盾：
  1. §2.2 MQTT `FamilyAccessGrantedEvent` 表（约行 1391–1399）明确包含 `reason` 字段（枚举 `REGULAR_60S | EMERGENCY_ACTIVATION | OCCLUSION_RECOVERY`）
  2. §4.1 ArkTS `AccessGrantedMessage` 接口（约行 1786–1792）包含 `reason: AccessGrantReason` 字段
  3. §4.1 家属 APP WebSocket 连接管理代码示例（约行 1842–1846）依赖 `msg.reason === 'EMERGENCY_ACTIVATION'` 实现自动接入 SparkRTC 的条件分支——若 WebSocket 消息不含 `reason`，该分支将永远为 `false`，导致高危失能场景下前端无法自动接入音视频
- **改进建议**：在 §3.1 `access_granted` 行 Payload 中补充 `"reason": "REGULAR_60S | EMERGENCY_ACTIVATION | OCCLUSION_RECOVERY"`，与 MQTT 事件和 ArkTS 接口保持一致

### 问题 2：§3.1 WebSocket `access_revoked` 下行消息 Payload 枚举值不完整

- **所在位置**：§3.1 下行消息表 `access_revoked` 行（约行 1495）
- **严重程度**：一般
- **问题描述**：§3.1 中 `access_revoked` 消息 Payload 仅展示单一 `reason` 值 `"RISK_DECLINED"`。但 §2.2 MQTT `FamilyAccessRevokedEvent` 表（约行 1401–1406）和 §4.1 ArkTS `AccessRevokeReason` 类型（约行 1647）均定义了三值枚举：`RISK_DECLINED | CAMERA_OCCLUDED | DRIVER_DEACTIVATED`。前端实现者仅阅读 §3.1 此表可能遗漏其余两种撤销原因的处理分支（如摄像头遮挡导致的权限撤销需提示"驾驶员已关闭摄像头"）
- **改进建议**：将 §3.1 `access_revoked` 行 Payload 中 `reason` 字段值从 `"RISK_DECLINED"` 更新为 `"RISK_DECLINED | CAMERA_OCCLUDED | DRIVER_DEACTIVATED"`

### 问题 3：§1.6 `QueryUpgradeHistoryResponse.finalStatus` 枚举值未定义

- **所在位置**：§1.6 `QueryUpgradeHistoryResponse` JSON 示例（约行 694–707）
- **严重程度**：一般
- **问题描述**：`QueryUpgradeHistoryResponse` 的 `entries[].finalStatus` 字段 JSON 示例中仅展示 `"SUCCEEDED"`，未枚举全部可能取值。经核实，应用层 OOD（`docs/ood_application.md:890`）已定义 `UpgradeFinalStatus` 枚举为 `SUCCEEDED | FAILED | ROLLED_BACK`。但 API OOD 未承接此枚举定义，前端开发者仅阅读本产出无法获知 `QueryUpgradeHistoryResponse` 中可能的 `finalStatus` 值，无法编写完整的 switch-case 处理逻辑
- **改进建议**：在 `QueryUpgradeHistoryResponse` JSON 示例后补充 `finalStatus` 取值说明：`SUCCEEDED | FAILED | ROLLED_BACK`，并标注与 `TriggerRollbackResponse.newStatus` 终态值的对应关系

### 问题 4：§1.6 S6 `400` 错误码覆盖场景不完整

- **所在位置**：§1.6 错误响应（约行 728–733）
- **严重程度**：轻微
- **问题描述**：§1.6 错误响应中 `400` 仅标注 `BatchSizeExceeded`（批量超限 >100 辆）。但 S6 的 `queryUpgradeProgress` 端点接受 `vehicleIds` 查询参数（comma-separated, required），当该参数格式无效（非逗号分隔、含非法字符、空字符串等）时，按 REST 惯例应返回 `400`，但当前错误响应中缺乏对应场景的 `AppError` 枚举映射。作为对比，S1/S2/S3 的 `400` 均覆盖了"参数无效"通用场景
- **改进建议**：将 §1.6 错误响应中 `400` 的描述从"批量超限（>100）：`BatchSizeExceeded`"扩展为覆盖通用参数无效场景，例如："`400` — 参数无效（批量超限：`BatchSizeExceeded`；查询参数格式错误等）"

### 问题 5：`POST /api/v1/sparkrtc/token` 端点的独立调用场景未阐明

- **所在位置**：§1.3 S3 端点表（约行 156）、§1.3 `IssueSparkRTCTokenRequest/Response`（约行 320–343）
- **严重程度**：轻微
- **问题描述**：家属 APP 获取 SparkRTC 入房凭证的正常路径为：(1) WebSocket `access_granted` 下行消息（§3.1）；(2) REST `POST /api/v1/guardianship/media-session` 响应的 `RequestMediaSessionResponse`（§1.3）。`POST /api/v1/sparkrtc/token` 独立端点在接受上述两条路径自动下发 Token 之外，其独立的调用时机和使用条件（如 WebSocket 断线后仅需续期 Token、主动切换视频清晰度后需重新入房等场景）未在文档任何位置说明。API 使用者可能不确定何时应主动调用此端点
- **改进建议**：在 §1.3 `IssueSparkRTCTokenResponse` 段落后新增"调用场景说明"块，列举典型调用时机（如 Token 过期前主动续期、网络恢复后重新入房等），并标注对于已持有 `access_granted` 中 Token 的常规场景无需额外调用

### 问题 6：§2.1 MQTT 主题路由表中 `app/{accountId}/rescue/confirm` 消费者归属不明确

- **所在位置**：§2.1 主题路由总表（约行 874）、§2.2 SOS 确认通知 Payload 表（约行 1432–1439）
- **严重程度**：轻微
- **问题描述**：Topic `app/{accountId}/rescue/confirm` 的描述为"推送→APP"且仅标注 SOS 上报结果通知（CONFIRMED / PENDING_RETRY），未指明目标 APP 是家属 APP 还是救援机构 APP。Payload 中的 `status` 枚举 `CONFIRMED / PENDING_RETRY / MANUAL_ESCALATION` 使用 S5 的生命周期状态体系（而非 S3 的 `PENDING / CONFIRMED / REJECTED`），暗示目标消费者为救援机构。但与同一"推送→APP"分组中面向家属的 `family/{accountId}/alert/push` 和 `family/{accountId}/status/push` 并列表述可能造成混淆
- **改进建议**：在 §2.1 该行描述中明确目标消费者，例如将"推送→APP"改为"推送→救援机构 APP"；或在 Payload 说明中标注"目标消费方：救援机构控制台 / 救援 APP"

---

## 结论

产出已达到可投入使用水平。上述 6 个问题均为局部补全性缺陷，不影响整体架构设计正确性。建议优先修复问题 1 和问题 2（影响前端实现逻辑的 WebSocket 消息字段缺失），其余问题可在下一轮迭代中一并修正。
