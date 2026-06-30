# 产出审查报告（v1）

## 审查结果

REJECTED

## 逐维度审查

### 1. 任务完备性

**[通过]** 问题 3（familyAccountId 安全门控）— §1.3 新增"安全门控"说明块（:145），§5.6 隐私校验表新增对应行（:1820），完整覆盖改进建议。

**[通过]** 问题 4（MQTT cmd/media/join/down 缺失）— §2.1 路由表已新增该主题行（:695），§2.2 已补充 Payload 字段定义表（:1263-1270），覆盖所有要求字段（commandId、sparkRTCRoomId、sparkRTCJoinToken、issuedAt）。

**[通过]** 问题 5（dataConsistency 字段缺失）— QueryTrajectoryResponse JSON 示例已补充字段（:379），枚举值和触发条件已说明（:383）。

**[通过]** 问题 6（requestMediaSession 前端指引不完整）— §4.1 新增"对讲建立通道说明"块（:1414），REST API 调用列表已补入该端点行（:1420），ArkTS DTO（RequestMediaSessionReq/Resp）已补充（:1446-1459）。

**[通过]** 问题 7（S6 表格列数异常）— cancelUpgradeTask 行已修复为 7 列（:563）。

**[通过]** 问题 8（ArkTS DTO 命名不一致）— §4.1 新增命名约定说明块（:1429），声明 Req=Request、Resp=Response 等价关系。

**[问题-严重]** S3→S5 救援 ID 体系断裂的修复不完整：§1.3 `TriggerManualRescueResponse` 已正确补充 `rescueReportId` 字段（:202），但 §4.1 对应的 ArkTS `TriggerManualRescueResp` 接口（:1475-1478）中缺失该字段，仅有 `rescueRequestId` 和 `status`。前端开发者依据 §4.1 实现时无法获知需要处理和传递 `rescueReportId`，导致修复后的 ID 关联链路在客户端侧断裂——前端无法以 `rescueReportId` 调用 S5 `queryRescueHistory` 查询救援进展。所在位置：§4.1 ArkTS interface 定义（:1475-1478），对比 §1.3 TriggerManualRescueResponse（:199-205）。

**[问题-严重]** 问题 2（新增 API 端点未映射到应用层方法）仍未根本解决：§1.3（:137-143）和 §1.6（:664-668）的"预签名"标注虽然升级为 v4 版并补充了更完整的方法签名描述，但 `docs/ood_application.md` 中仍未实际添加这 3 个方法。原始需求约束 §49 要求"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"，预签名本质仍为承诺性标注。所在位置：§1.3 预签名块（:137-143）、§1.6 预签名块（:664-668），对比 `docs/ood_application.md` §3.3 和 §3.6。

### 2. 质量达标性

**[通过]** 问题 1 的修复在各处（REST 响应、流转说明块、WebSocket 下行消息、修订说明）之间基本一致，仅 §4.1 遗漏 `rescueReportId`。

**[通过]** 问题 3 的安全门控在 §1.3 说明块和 §5.6 校验表之间一致。

**[通过]** 问题 4 的 MQTT 主题在路由表和 Payload 定义之间一致，流程图引用（:1351）与路由表对齐。

**[通过]** 修订说明（v4）准确记录了 8 个问题的处理方式，与实际修改内容一致。

**[问题-一般]** §4.1 "对讲建立通道说明"块（:1414）称 REST 降级方案返回的 `RequestMediaSessionResponse` 含 `sessionHandle` + `sparkRTCJoinToken`，但 REST 响应模型（:162-169）实际包含 4 个字段（sessionHandle、sessionToken、sparkRTCRoomId、sparkRTCJoinToken）。说明块的简化描述可能误导前端开发者忽略 `sessionToken` 和 `sparkRTCRoomId` 字段的必要处理。所在位置：§4.1 说明块（:1414）与 §1.3 RequestMediaSessionResponse（:162-169）。

### 3. 正确性

**[通过]** 问题 1 的流转说明块（:211）描述了合理的编排逻辑（S3 内部调用 S5 createRescueReport），与领域模型中的跨服务协作语义一致。

**[通过]** 问题 4 新增的 SparkRTC 入房凭证 Payload 定义（:1263-1270）与 §3.2 流程图（:1351-1354）中使用的字段（sparkRTCRoomId、sparkRTCJoinToken）一致。

**[通过]** 问题 5 的 dataConsistency 枚举值（CONSISTENT / INCONSISTENT）及触发条件与对应的端点描述（:383）逻辑自洽。

## 修改要求（存在严重或一般问题时）

- **问题**：ArkTS `TriggerManualRescueResp` 接口缺失 `rescueReportId` 字段
- **原因**：§1.3 `TriggerManualRescueResponse` 已通过 v4 修复补充了 `rescueReportId`（问题 1），但 §4.1 对应的 ArkTS 数据模型未同步更新。前端开发者依据 §4.1 实现救援触发响应处理时，无法获知该字段的存在，无法将 `rescueReportId` 传递给 S5 的 `queryRescueHistory` 查询救援进展。这导致 S3→S5 ID 关联链路在客户端侧断裂，问题 1 的修复仅在后端契约层面完成。
- **建议方向**：在 §4.1 `TriggerManualRescueResp` interface 中补充 `rescueReportId: string` 字段，与 REST 响应模型保持一致。

- **问题**：新增 API 端点的应用层方法仍未在 `docs/ood_application.md` 中实际定义
- **原因**：v4 的"预签名"标注虽然比 v3 更详细（含入参来源、返回模型、校验约束），但本质上仍是承诺性标注而非实际方法定义。原始需求约束 §49 要求端点映射到"已定义"的方法。此问题已持续 3 轮（v2 → v3 → v4），持续以升级标注形式处理而非根本解决，下游工作（如服务实现、集成测试）将因方法定义缺失而受阻。
- **建议方向**：在 `docs/ood_application.md` 的 §3.3 `IRemoteGuardianshipService` 中补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 的方法签名与 DTO 定义；在 §3.6 `IOTAManagementService` 中补充 `cancelUpgradeTask` 的方法签名与 DTO 定义。然后将 API OOD 中的"预签名"标注更新为对应用层方法签名的映射引用（章节号 + 行号）。

- **问题**：§4.1 "对讲建立通道说明"块对 REST 响应字段的简化描述不完整
- **原因**：说明块仅提及 `sessionHandle` + `sparkRTCJoinToken` 两个字段，但实际 REST 响应包含 4 个字段。前端开发者若仅阅读此说明而跳过 §1.3 的完整模型定义，可能遗漏对 `sessionToken` 和 `sparkRTCRoomId` 的处理，影响后续 SparkRTC 房间加入流程。
- **建议方向**：将说明块中的字段列举更新为完整 4 项（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），或删除简化列举改为直接引用 §1.3 的 `RequestMediaSessionResponse` 模型。
