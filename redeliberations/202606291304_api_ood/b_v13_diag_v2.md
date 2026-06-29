# API/接口层 OOD 质量审查报告（v13 / v2）

> 审查轮次：第 13 次迭代 · 审查报告 v2（基于 v1 报告 + 质询 `b_v13_challenge_v1.md` 修订）
> 审查范围：需求响应充分度、整体深度与完整性、事实错误与逻辑矛盾、**跨文档（API OOD ↔ 应用层 OOD）契约一致性**
> 待审查文件：`a_v13_copy_from_v12.md`
> 审查基准：`requirement.md`、`docs/ood_application.md`、`docs/ood_domain.md`
> 行号说明：本版全部位置引用均经逐行核对，给出精确行号。

---

## 审查方法与核查依据

本版审查在 v1 基础上补充了三类实证核查（v1 缺失，导致被质询）：

1. **第 12 轮 8 个问题修复状态逐项核查** —— 见附录 A。
2. **历史高优先级问题（第 9/11 轮）修复状态核查** —— 见附录 B。
3. **API OOD ↔ 应用层 OOD 跨文档 DTO 一致性核查** —— 发现 3 项仍未消解的跨层不一致（见问题 7–9），其中 1 项历史定级为「严重」。

核查结论：第 12 轮 8 项均已落实；但第 4–10 轮反复报出的**跨层 DTO 一致性缺陷未全部消解**，v1 报告对此为空白，结论「产出已达到可投入使用水平」不成立，本版予以修正。

---

## 发现的质量问题

### 问题 1（严重程度上调：一般 → 中等）：§3.1 WebSocket `access_granted` 下行消息 Payload 缺少 `reason` 字段

- **所在位置**：`a_v13_copy_from_v12.md:1494`（§3.1 下行消息表 `access_granted` 行）
- **严重程度**：中等（v1 定为「一般」，经质询核实后上调）
- **问题描述**：`access_granted` 行 Payload 定义为 `{ "driverId": "...", "sessionToken": "...", "sparkRTCRoomId": "...", "sparkRTCJoinToken": "..." }`，不含 `reason`。但：
  1. §2.2 MQTT `FamilyAccessGrantedEvent` 表（`:1391–1399`）含 `reason` 字段（`REGULAR_60S | EMERGENCY_ACTIVATION | OCCLUSION_RECOVERY`）；
  2. §4.1 ArkTS `AccessGrantedMessage`（`:1786–1792`）含 `reason: AccessGrantReason`；
  3. §4.1 代码示例（`:1842–1847`）以 `if (msg.reason === 'EMERGENCY_ACTIVATION')` 作为高危失能场景自动接入 SparkRTC 的判定分支。
  若后端严格按 §3.1 表构造 WebSocket 消息而省略 `reason`，前端该分支永远为 `false`，**高危失能场景下家属端无法自动接入音视频**——直接影响安全救援链路功能正确性。上调为「中等」的依据：缺陷落在 BR-06 紧急救援路径上；之所以未定为「严重」，是因 MQTT 事件与 ArkTS 接口均已正确定义 `reason`，谨慎实现者可据交叉引用补回，非不可恢复。
- **改进建议**：在 §3.1 `access_granted` 行 Payload 补 `"reason": "REGULAR_60S | EMERGENCY_ACTIVATION | OCCLUSION_RECOVERY"`，与 MQTT 事件、ArkTS 接口对齐。

### 问题 2（一般）：§3.1 WebSocket `access_revoked` 下行消息 Payload 枚举值不完整

- **所在位置**：`a_v13_copy_from_v12.md:1495`（§3.1 下行消息表 `access_revoked` 行）
- **严重程度**：一般
- **问题描述**：`access_revoked` Payload 仅示单值 `"RISK_DECLINED"`。但 §2.2 `FamilyAccessRevokedEvent`（`:1401–1406`）与 §4.1 ArkTS `AccessRevokeReason`（`:1647`）均定义三值枚举 `RISK_DECLINED | CAMERA_OCCLUDED | DRIVER_DEACTIVATED`。仅读 §3.1 的实现者易遗漏其余两种撤销原因分支（如摄像头遮挡撤销需提示"驾驶员已关闭摄像头"）。
- **改进建议**：将该行 `reason` 值改为 `"RISK_DECLINED | CAMERA_OCCLUDED | DRIVER_DEACTIVATED"`。

### 问题 3（一般）：§1.6 `QueryUpgradeHistoryResponse.finalStatus` 枚举值未在 API OOD 中定义

- **所在位置**：`a_v13_copy_from_v12.md:702`（`QueryUpgradeHistoryResponse` JSON 示例 `finalStatus` 字段，整体示例 `:692–707`）
- **严重程度**：一般
- **问题描述**：`entries[].finalStatus` JSON 示例仅示 `"SUCCEEDED"`，未枚举全部取值。应用层 OOD `docs/ood_application.md:890` 已定义 `UpgradeFinalStatus = SUCCEEDED | FAILED | ROLLED_BACK`，但 API OOD 未承接，前端仅读本产出无法编写完整的状态分支处理。
- **改进建议**：在该 JSON 示例后补 `finalStatus` 取值说明 `SUCCEEDED | FAILED | ROLLED_BACK`，并标注与 `TriggerRollbackResponse.newStatus` 终态值（`ROLLED_BACK`）的对应关系。

### 问题 4（轻微）：§1.6 S6 `400` 错误码覆盖场景不完整

- **所在位置**：`a_v13_copy_from_v12.md:729`（§1.6 错误响应 `400` 行，整体 `:728–732`）
- **严重程度**：轻微
- **问题描述**：`400` 仅标 `BatchSizeExceeded`。但 `queryUpgradeProgress`（`:622`）接受必填查询参数 `vehicleIds`（逗号分隔），当其格式非法（非逗号分隔、含非法字符、空串）时按 REST 惯例应返回 `400`，却无对应 `AppError` 映射。S1/S2/S3 的 `400` 均覆盖了"参数无效"通用场景。
- **改进建议**：将 `400` 描述扩展为覆盖通用参数无效场景，例如"`400` — 参数无效（批量超限 `BatchSizeExceeded`；`vehicleIds` 格式错误等）"。

### 问题 5（轻微）：`POST /api/v1/sparkrtc/token` 端点的独立调用场景未阐明

- **所在位置**：`a_v13_copy_from_v12.md:156`（§1.3 端点表）、`:320–343`（`IssueSparkRTCTokenRequest/Response`）
- **严重程度**：轻微
- **问题描述**：家属 APP 获取 SparkRTC 入房凭证的常规路径为 §3.1 WebSocket `access_granted` 与 §1.3 `RequestMediaSessionResponse`，二者均自动下发 Token。`POST /api/v1/sparkrtc/token` 作为独立端点，其独立调用时机（如 Token 过期续期、断线重连后重新入房）未在文档任何处说明，使用者不确定何时应主动调用。
- **改进建议**：在 `IssueSparkRTCTokenResponse`（`:334–343`）后补"调用场景说明"，列举典型时机，并注明常规场景（已持有 `access_granted` 内 Token）无需额外调用。

### 问题 6（轻微）：§2.1 `app/{accountId}/rescue/confirm` 消费者归属不明确

- **所在位置**：`a_v13_copy_from_v12.md:873`（§2.1 主题路由总表）、`:1432–1439`（§2.2 SOS 确认通知 Payload 表）
- **严重程度**：轻微
- **问题描述**：该 Topic 描述为"推送→APP"，但其 Payload `status` 枚举 `CONFIRMED / PENDING_RETRY / MANUAL_ESCALATION` 采用 S5 生命周期状态体系（非 S3 的 `PENDING / CONFIRMED / REJECTED`），暗示消费者为救援机构；却与同组面向家属的 `family/{accountId}/...` 并列表述，易混淆目标 APP。
- **改进建议**：在该行描述明确目标消费方（如"推送→救援机构 APP"），或在 Payload 说明标注"目标消费方：救援机构控制台 / 救援 APP"。

---

### 问题 7（严重）：跨文档不一致 —— 应用层 `RequestMediaSessionResponse` 缺少 `sparkRTCRoomId`、`sparkRTCJoinToken`

- **所在位置**：API OOD `a_v13_copy_from_v12.md:184–196`（4 字段）、`:1669–1674`（ArkTS `RequestMediaSessionResp` 4 字段）、`:1606`（声明 REST 备用通道返回四字段且"与 WebSocket 通道完全一致"）；对比 `docs/ood_application.md:584–586`（仅 `sessionHandle`、`sessionToken` 两字段）
- **严重程度**：严重
- **问题描述**：API OOD 的 `RequestMediaSessionResponse` 定义 4 字段（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），且 §4.1 明确将 REST `POST /api/v1/guardianship/media-session` 作为 WebSocket 不可用时的**对讲建立降级通道**。但应用层 OOD 的同名 DTO 仅含 2 字段，缺 `sparkRTCRoomId`、`sparkRTCJoinToken`。后果：应用层 `requestMediaSession(...): RequestMediaSessionResponse`（`docs/ood_application.md:266`）无法返回入房凭证，**REST 降级路径事实上无法交付 SparkRTC 入房凭证**，文档自述的"两种通道返回数据模型完全一致"不成立。此缺陷违反需求约束 `requirement.md:48`（"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"）。该问题自第 7 轮（严重）、第 10 轮（一般）报出，至 v13 仍未消解。
- **改进建议**：在 `docs/ood_application.md:584–586` 的 `RequestMediaSessionResponse` 中补 `sparkRTCRoomId: String` 与 `sparkRTCJoinToken: String`；补齐后在本产出 §1.3 / §4.1 标注跨层 DTO 已对齐。

### 问题 8（中等）：跨文档不一致 —— 应用层 `RequestMediaSessionRequest` / `TriggerManualRescueRequest` 缺少 `secondaryAuthToken`

- **所在位置**：API OOD `a_v13_copy_from_v12.md:177`（`RequestMediaSessionRequest.secondaryAuthToken`）、`:216`（`TriggerManualRescueRequest.secondaryAuthToken`）；对比 `docs/ood_application.md:577–580`（`RequestMediaSessionRequest` 仅 3 字段，无 `secondaryAuthToken`）、`:596–598`（`TriggerManualRescueRequest` 仅 2 字段，无 `secondaryAuthToken`）
- **严重程度**：中等
- **问题描述**：API OOD 将 `secondaryAuthToken` 列入两个高敏操作请求体，并在 §5.2（`:1971–1975`）将二次验证门控置于 `requestMediaSession` / `triggerManualRescue` 入口。但应用层 OOD 的同名 DTO 缺该字段——前端传入的二次验证凭证在应用服务入口反序列化时丢失，**§5.2 声明的二次身份验证门控无法在这两个入口落地**。注意：`ControlVehicleWindowRequest`（`docs/ood_application.md:611`）已含 `secondaryAuthToken`，故缺失是部分性的、不一致的。该问题自第 4/5/10 轮报出，至今未消解。
- **改进建议**：在 `docs/ood_application.md` 的 `RequestMediaSessionRequest`（`:577–580`）与 `TriggerManualRescueRequest`（`:596–598`）中补 `secondaryAuthToken: String`，与 `ControlVehicleWindowRequest` 保持一致。

### 问题 9（轻微）：跨文档不一致 —— `StatusColor` 枚举在应用层 OOD 仍含领域层未定义的 `ORANGE`

- **所在位置**：API OOD `a_v13_copy_from_v12.md:53`（三值 + 反向兼容说明）；对比 `docs/ood_application.md:499`（`derivedStatusColor` 注释仍为 `GREEN / YELLOW / ORANGE / RED`）、领域层 `docs/ood_domain.md`（VO-15 `StatusColor` 三值）
- **严重程度**：轻微
- **问题描述**：API OOD 本身已统一为三值 `GREEN / YELLOW / RED`，并加注"若应用层引用额外值（如 ORANGE），应以领域层定义为准"。但根因——应用层 OOD `:499` 仍保留四值（含 `ORANGE`）——未消除，领域层↔应用层枚举矛盾依旧存在。API OOD 以注释回避而非根治。该问题自第 6/10 轮报出。
- **改进建议**：在 `docs/ood_application.md:499` 移除 `ORANGE`，统一为 `GREEN / YELLOW / RED`；之后本产出 §1.1 行 53 的反向兼容说明可简化或删除。

---

## 附录 A：第 12 轮 8 个问题修复状态核查

| # | 第 12 轮问题 | v13 修复位置（精确行号） | 状态 |
|:--:|------|------|:--:|
| 1 | MQTT 干预指令 Topic 引用"车队管理员远程鸣笛"无触发入口 | §2.1 `:855` 已改为"由 S1 风险判定驱动…云端经 IoTDA 关联下发" | 已修复 |
| 2 | `sessionToken` 与 `sparkRTCJoinToken` 语义重叠未区分 | §1.3 `:194–196` 明确二者用途；§4.1 `:1845` 示例用 `sparkRTCJoinToken` 调 join | 已修复 |
| 3 | §4.1 缺三个 Auth 端点 | §4.1 `:1621–1623` 已补 login/refresh/secondary-verify 三行 | 已修复 |
| 4 | JWT `scope` 授权规则不明 | §5.1 JWT Payload `:1940–1945` 已移除 `scope`，仅保留 `sub/role/iat/exp`（采纳方案 b） | 已修复 |
| 5 | 关键数据保留/归档/清理策略缺失 | 新增 §5.7「数据生命周期与保留策略」`:2065–2080` | 已修复 |
| 6 | S4 轨迹查询无时间跨度/分页上限约束 | §1.4 `:425–428` 补"最大 30 天、size 默认 100 上限 500、超限截断" | 已修复 |
| 7 | §4.1 漏 3 个 WebSocket ArkTS 接口 | §4.1 `:1800–1815` 补 `TokenRenewedMessage`/`SubscribeStatusAckMessage`/`RescueTriggeredMessage` | 已修复 |
| 8 | MQTT 推送阶段 GPS 脱敏规则缺失 | §5.6 `:2063` 新增推送场景 GPS 权限校验行（与 pull 场景对称） | 已修复 |

结论：第 12 轮 8 项**全部已落实**（v1 的"均已落实"结论经核查成立，但 v1 未提供任何核查依据，本附录补齐）。

## 附录 B：历史高优先级问题修复状态核查

| 轮次/级别 | 问题 | 核查结果（精确行号） | 状态 |
|:--:|------|------|:--:|
| 第 9 轮（严重） | `IEmergencyRescueService.createRescueReport()` 接口契约缺失 | `docs/ood_application.md:403`（方法行）+ `:827/:833`（DTO）已落地；本产出 §1.5 `:611`、§1.3 `:236` 正确引用 | 已修复 |
| 第 11 轮（中等） | JWT refresh token 端点缺失 | §1.7 `:743` 已定义 `POST /api/v1/auth/refresh`（含 `:777–800` DTO） | 已修复 |
| 第 11 轮（一般） | REST 标准错误响应体格式未定义 | §一 `:16–28` 已定义 `{errorCode,message,requestId}` 统一格式 | 已修复 |
| 第 11 轮（中等） | S5/S6 缺基础错误码 | S5 `:605–609`（400/403/404/409）、S6 `:728–732`（400/404/409/503）已补 | 已修复 |
| 第 11 轮（一般） | S4 缺 DELETE 取消订阅端点 | §1.4 `:369` 已补 `DELETE …/performance-warning-subscription/{subscriptionId}` | 已修复 |
| 第 4/5/10 轮 | 跨层 DTO 一致性（`RequestMediaSessionResponse`、`secondaryAuthToken`、`StatusColor`） | **部分未修复** —— 详见本报告问题 7、8、9 | **未消解** |

## 附录 C：安全设计（§5）专项核查

逐项核对需求 `requirement.md:41–43` 的安全设计要求：

- **API 认证（JWT/OAuth2）**：§5.1 `:1925–1963` 提供 JWT 结构、签发/校验流程、角色→权限映射，并对"不采用 OAuth2"给出五点设计理由（`:1927–1934`），覆盖充分。
- **接口限流（令牌桶/漏桶）**：§5.3 `:1991–2005` 按角色/端点分级令牌桶 + 突发容忍 + 429 + Retry-After，覆盖充分。
- **MQTT 设备鉴权（X.509/Token）**：§5.4 `:2007–2026` X.509 + 一机一密备选 + TLS 套件 + 注册流程，覆盖充分。
- **敏感数据加密**：§5.5 `:2028–2047` 分数据类别的传输/应用/存储三层加密 + KMS 密钥管理，覆盖充分。
- **BR-04 隐私边界**：§5.6 `:2049–2063` 隐私校验点（含 push/pull 对称 GPS 校验、横向越权 `familyAccountId` 校验），覆盖充分。
- **数据生命周期**：§5.7 `:2065–2080` 已补。

结论：§5 安全设计已充分响应需求，第 12 轮安全相关项（问题 4 scope、问题 5 生命周期、问题 8 推送脱敏）均已修复，**未发现 §5 内的显著残留缺陷**。（说明：v1 报告对 §5 完全未做核查声明，本附录补齐质询要求的安全维度核查。）

---

## 结论（修正 v1）

v1 结论"产出已达到可投入使用水平、无阻塞性缺陷"**不成立**，予以修正：

- 产出在 REST/MQTT/WebSocket/ArkTS/安全五部分上对需求的响应已较为完整，第 12 轮及多数历史问题均已修复；
- 但仍存在 **1 项严重（问题 7）+ 1 项中等（问题 8）的跨层契约不一致**，使 REST 对讲降级路径与高敏操作二次验证门控在应用层 OOD 中无法落地，违反需求约束 `requirement.md:48`，构成投入使用前必须消解的阻塞项；
- 另有问题 1（中等，影响安全自动接入）及问题 2–6、9 等若干补全性缺陷。

建议优先级：问题 7 > 问题 1、问题 8 > 问题 2、3 > 问题 9、4、5、6。

---

## 修订说明（v2）

| 质询意见 | 回应 |
|---------|------|
| 证据充分性：宣称"8 个修复已落实"但未枚举、无验证依据 | 接受。新增**附录 A**逐项列出第 12 轮 8 个问题的修复位置（精确行号）与状态，全部核实为已修复。 |
| 证据充分性：全部位置引用为"约行"，与"实际核查"自相矛盾 | 接受。本版全部位置引用改为**精确行号**（逐行核对 `a_v13_copy_from_v12.md` 及两份 OOD 文档）。 |
| 逻辑完整性：未核查跨文档 DTO 一致性即断言"可投入使用" | 接受且证实。核查发现 3 项跨层不一致仍未消解，新增**问题 7（严重）、8（中等）、9（轻微）**，并据此**修正结论**：产出未达可投入使用水平，存在阻塞项。 |
| 逻辑完整性：问题 1 严重程度被低估 | 部分接受。问题 1 由"一般"**上调为"中等"**（落在 BR-06 安全救援路径）；未上调至"严重"，因 MQTT 事件与 ArkTS 接口均已正确定义 `reason`，存在交叉引用可恢复，已在问题 1 中说明定级依据。 |
| 覆盖完备性：6 个问题无一涉及 §5 安全设计，安全维度审查为空白 | 接受。新增**附录 C**对 §5 五项需求逐项核查，确认安全设计已充分响应需求且第 12 轮安全相关项均修复，未发现 §5 显著残留缺陷（如实说明，不为凑问题而虚列）。 |
| 覆盖完备性：未对历史高优先级问题（第 9/11 轮）做修复可达性判断 | 接受。新增**附录 B**列出第 9/11 轮各高优先级问题的修复状态与精确位置；其中第 4/5/10 轮跨层 DTO 项标记为"未消解"并指向问题 7/8/9。 |
