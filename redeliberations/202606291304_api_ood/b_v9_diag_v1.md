# API/接口层 OOD 设计方案 质量审查报告（第 9 轮）

## 审查范围

审查维度（侧重内部审议未充分覆盖的角度）：需求响应充分度、整体深度和完整性、事实错误与逻辑矛盾。从下游使用者视角评估产出是否可直接投入使用。

审查依据：`requirement.md`、`docs/ood_application.md`。

---

## 质量问题清单

### 问题 1（严重 · 接口契约缺失 · 持久未解）

**描述**：S3→S5 手动救援流转依赖 `IEmergencyRescueService.createRescueReport` 方法，该方法的调用在产出 §1.3 TriggerManualRescueResponse（:214–221）"S3→S5 手动救援流转说明"及"⚠ 接口契约待补"块中明确引用，但 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表（:397–402）仅包含 4 个方法（`confirmSOSReport`、`issueRescueToken`、`verifyRescueToken`、`queryRescueHistory`），**不存在** `createRescueReport` 方法签名及其对应的 Request/Response DTO。

此问题自第 4 轮迭代即被识别，历经 5 轮迭代（v4→v9）以"⚠ 接口契约待补"嵌标注方式存在，至今仍在应用层 OOD 中未落地。下游 S3 实现者在依赖此方法的情况下无法完整实现手动救援流的编排逻辑。当前产出中"待应用层 OOD 补全"的声明减轻了自身责任，但并未消除消费方的不确定性。

**所在位置**：产出 §1.3 TriggerManualRescueResponse / S3→S5 手动救援流转说明（:214–221）；对比 `docs/ood_application.md` §3.5 接口方法表（:397–402）。

**严重程度**：严重

**改进建议**：在 `docs/ood_application.md` §3.5 接口方法表中补齐 `createRescueReport` 方法行（含输入 DTO、输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 `CreateRescueReportRequest`/`CreateRescueReportResponse` DTO 定义。完成后移除产出中的"⚠ 接口契约待补"标注。

---

### 问题 2（中等 · 文档结构错误 · 重复修订块）

**描述**：产出末尾存在两个题为"修订说明（v9）"的独立修订块（第一个 :2028–2038，第二个 :2053–2059），导致文档结构混乱。第二个 v9 修订块修复了内部版本编号不一致（"v10" → "v9"），但修复后的结果是与第一个 v9 块共存，阅读者无法判断应参考哪一版修订信息。正确的处理方式应为将第二个修订块的内容合并入第一个 v9 块，而非新增独立块。

**所在位置**：产出 :2028–2038（第一个"修订说明（v9）"）与 :2053–2059（第二个"修订说明（v9）"）。

**严重程度**：中等

**改进建议**：将第二个 v9 修订块的内容合并至第一个 v9 修订块末尾，删除重复的"修订说明（v9）"标题和独立的修订块结构。

---

### 问题 3（中等 · 枚举定义不完整 · 逻辑矛盾）

**描述**：`TriggerRollbackResponse.newStatus` 包含值 `ROLLING_BACK`（产出 §1.6 :657），且产出建议"前端应在收到 `ROLLING_BACK` 后持续轮询 `queryUpgradeProgress` 直至 `currentStage` 变为 `ROLLED_BACK`"。但 `QueryUpgradeProgressResponse.currentStage` 枚举（:637）定义为 `PENDING | TRANSMITTING | VERIFYING | READY | UPGRADING | COMPLETED | ROLLED_BACK`，**不包含 `ROLLING_BACK`**。

这意味着在回滚执行期间，`queryUpgradeProgress` 返回的 `currentStage` 无法表达"正在回滚中"这一中间状态（可能停留在上一阶段值如 `UPGRADING`），前端无有效手段区分"仍在正常升级"与"正在回滚"，轮询终止条件 `currentStage == ROLLED_BACK` 之前的过渡期完全不可观测。

**所在位置**：产出 §1.6 TriggerRollbackResponse（:657）与 QueryUpgradeProgressResponse（:637）。

**严重程度**：中等

**改进建议**：二选一：(a) 在 `currentStage` 枚举中补充 `ROLLING_BACK` 值，使回滚中间状态可被轮询观测；(b) 若回滚为瞬时操作（无需中间状态），则在 TriggerRollbackResponse 中移除 `ROLLING_BACK` 值，仅保留 `ROLLED_BACK`，并修改前端指导为直接读取终态。

---

### 问题 4（一般 · 数据链路缺失 · 完整性不足）

**描述**：产出 §2.2 `vehicle/state/up` MQTT 主题的 VehicleStateSnapshot JSON Schema（:1156–1180）仅包含 `speed`、`doorLockState`、`acceleration`、`fuelLevel`、`odometer` 字段，**不包含车窗状态字段**。但以下位置均依赖车窗状态数据：

- §1.3 REST `GET /api/v1/vehicles/{vehicleId}/windows` 返回的 `QueryWindowStatusResponse`（:239–256）；
- §2.2 MQTT 推送 `DriverStatusSnapshot` 的 `windowStatus` 字段（:879–891）。

当前设计中车窗状态的唯一来源是 `cmd/window/down` 下发后的 Ack 响应，缺少**周期性或事件驱动的车窗状态遥测上报**。这导致 `QueryWindowStatusResponse` 在无最近车窗控制指令时数据可能为空或过期，`DriverStatusSnapshot.windowStatus` 推送的数据来源也不明确。

**所在位置**：产出 §2.2 VehicleStateSnapshot（:1156–1180）vs §1.3 QueryWindowStatusResponse（:239–256）、§2.2 DriverStatusSnapshot.windowStatus（:879–891）。

**严重程度**：一般

**改进建议**：在 `vehicle/state/up` VehicleStateSnapshot 中增加 `windowStatuses` 字段（复用 `WindowStatusEntry` 结构），或新增独立的车窗状态上报 topic（如 `{deviceId}/vehicle/window/up`），确保车窗状态有明确的数据来源并可被 REST 查询和 MQTT 推送消费。

---

### 问题 5（轻微 · 命名不一致 · 接口层不统一）

**描述**：产出 §2.2 MQTT SafetyAlertEvent JSON Schema 中的 GPS 字段命名为 `gps`（:830–836），但 §1.1 REST `QueryAlertHistoryResponse` 中对应字段命名为 `gpsLocation`（:53）。产出 §2.1 路由表说明（:60）声明两者"保持一致"，但实际命名不同。虽然语义等价，但在跨协议（REST / MQTT）的接口文档中字段名不一致会增加消费方的理解成本。

**所在位置**：产出 §2.2 SafetyAlertEvent `gps` 字段（:830）vs §1.1 QueryAlertHistoryResponse `gpsLocation` 字段（:53）。

**严重程度**：轻微

**改进建议**：统一为 `gpsLocation` 或增加显式字段映射说明块。

---

## 整体质量评价

产出覆盖了需求文档要求的所有五个设计部分（REST API、MQTT、WebSocket/SparkRTC、ArkTS 前端、安全设计），API 端点映射完整，MQTT 主题覆盖充分，安全设计层次完整。经过 9 轮迭代后多数已知问题已修复。

当前残留的 5 个问题中，问题 1（`createRescueReport` 接口契约缺失）为阻滞性问题——下游 S3 实现者依赖此方法完成手动救援编排，若不在下一轮中解决将造成实际阻塞。问题 2（重复修订块）为文档结构瑕疵，问题 3（`ROLLING_BACK` 枚举缺失）为可观测性缺口，问题 4 为数据链路完整性缺口。问题 5 为命名瑕疵。

此外，产出中存在的跨层 DTO 不一致（`AlertSummary.gpsLocation`、`QueryTrajectoryResponse.dataConsistency`、`RequestMediaSessionRequest.secondaryAuthToken`、`TriggerManualRescueRequest.secondaryAuthToken`、`RequestMediaSessionResponse.sparkRTCRoomId/sparkRTCJoinToken`、`StatusColor.ORANGE`）已在多轮迭代中被识别并在修订说明中标注为"保留（无需修改本文件）"，问题根源在 `docs/ood_application.md` 中，API OOD 自身定义完整。此处不在问题清单中重复列出，但建议关注累积风险：若应用层 OOD 长期不与 API OOD 同步，下游实现阶段会出现 DTO 反序列化失败。

---

## 修订说明（v1）

首轮审查，无修订历史。
