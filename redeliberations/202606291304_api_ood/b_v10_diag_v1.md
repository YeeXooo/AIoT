# API/接口层 OOD 质量审查报告（v10）

> 审查轮次：第 10 轮首审
> 审查日期：2026-06-29
> 待审查产出：`a_v10_copy_from_v9.md`
> 审查视角：使用者视角 — 产出是否可直接投入使用，边界情况和异常处理是否完备，需求响应是否充分。

---

## 一、问题清单

### 问题 1：缺少 JWT 登录/Token 签发端点，认证链路不完整

- **问题描述**：§5.1 描述了完整的 JWT 认证流程（用户登录 → IAM 签发 JWT → API 网关校验 → 应用服务提取 `sub`/`role`），但 §1 REST API 契约中未定义任何 JWT 签发端点（如 `POST /api/v1/auth/login`）。§1.7 仅定义了 `POST /api/v1/auth/secondary-verify` 二次验证端点。没有初始 Token 签发端点，家属 APP、车队大屏、救援机构控制台三者均无法获取 JWT，整个认证链路缺少起点。调用方拿到本文档后无法获知如何获得 Bearer Token。
- **所在位置**：§1 REST API 契约（缺失），§5.1（:1826–1832 描述了签发流程但缺少对应端点定义）
- **严重程度**：严重
- **改进建议**：在 §1.7 或新增 §1.8 中补充 `POST /api/v1/auth/login` 端点定义（含 `LoginRequest` / `LoginResponse`，支持用户名/密码和手机验证码两种登录方式），并明确其跳过 JWT 校验（为匿名端点）。

### 问题 2：MQTT AlertTriggeredEvent（family push）GPS 字段命名未随 SafetyAlertEvent 同步更新，文档内部不一致

- **问题描述**：v10 修订已将 §2.2 `SafetyAlertEvent` 的 GPS 字段从 `gps` 重命名为 `gpsLocation`（:830），与 REST `QueryAlertHistoryResponse` 保持一致。但 §2.2 末尾"家属告警推送（`family/{accountId}/alert/push`）— AlertTriggeredEvent"表（:1288）的 GPS 字段仍命名为 `gps`。同时 `trip/status/up`（:1126）和 `driver/override/up`（:1207）的 GPS 字段也使用 `gps`。文档在 v10 中声明的"统一为 `gpsLocation`"并未完全执行，造成同一文档内 GPS 字段命名不一致，影响前端/设备端对 Payload 字段名的理解。
- **所在位置**：§2.2 AlertTriggeredEvent 表（:1288）、TripStatus 事件（:1126）、OverrideSignal（:1207），对比 SafetyAlertEvent（:830）和 DriverStatusSnapshot（:862）
- **严重程度**：中等
- **改进建议**：二选一：(a) 将文档内所有 MQTT Payload 的 GPS 字段统一为 `gpsLocation`，包括 AlertTriggeredEvent、TripStatus、OverrideSignal；(b) 若各 Topic 的 GPS 字段名有意不同（如上报类与推送类语义差异），则在 §2.2 开头新增字段命名约定说明块，明确解释各 Topic 的 GPS 字段命名规则及其设计理由。

### 问题 3：跨文档 DTO 不一致 — `RequestMediaSessionResponse` 应用层 OOD 缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`

- **问题描述**：API OOD §1.3 `RequestMediaSessionResponse` 定义了 4 个字段（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），ArkTS `RequestMediaSessionResp` 也包含相同 4 个字段。但 `docs/ood_application.md` §4.3 同名 DTO 仅定义了 `sessionHandle` 和 `sessionToken` 两个字段（:584–586），缺少后端实现所需的 `sparkRTCRoomId` 和 `sparkRTCJoinToken`。该问题自第 7 轮首次发现以来持续未解决，v10 修订说明标记为"保留（无需修改本文件）"。下游实现者若以应用层 OOD 为开发依据，将遗漏前端入房所需的两个关键字段。
- **所在位置**：API OOD §1.3（:167–175）、§4.1（:1563–1568），对比 `docs/ood_application.md` §4.3（:584–586）
- **严重程度**：中等
- **改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段。

### 问题 4：跨文档 DTO 不一致 — `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 缺少 `secondaryAuthToken`

- **问题描述**：API OOD §1.3 的 `RequestMediaSessionRequest`（:154–163）和 `TriggerManualRescueRequest`（:194–202）均包含 `secondaryAuthToken` 字段。但 `docs/ood_application.md` §4.3 中对应的同名 DTO `RequestMediaSessionRequest`（:577–580）仅含 `familyAccountId`、`driverId`、`sessionType`，`TriggerManualRescueRequest`（:596–598）仅含 `familyAccountId`、`driverId`，两者均缺少 `secondaryAuthToken`。该问题自第 4 轮发现，第 5/6/7/8/9 轮持续标记为"保留（无需修改本文件）"但根源未解决。二次身份验证凭证在反序列化时将丢失，导致 §5.2 的高敏操作门控无法生效。
- **所在位置**：API OOD §1.3（:161, :200），对比 `docs/ood_application.md` §4.3（:577–580, :596–598）
- **严重程度**：中等
- **改进建议**：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

### 问题 5：S3 家属权限管理只有查询端点，缺少主动管理入口

- **问题描述**：需求 `requirement.md:23` 要求 S3 覆盖"家属权限查询/管理"。产出提供了 `GET /api/v1/guardianship/{driverId}/permissions` 查询端点，但权限的授予与撤销完全由系统侧自动化流程（60s 常规授予、高危自动激活、风险回落撤销等）驱动。家属 APP 缺少主动管理权限的入口：当家属不再需要监控某驾驶员时，无法主动撤销自己的监护权限；当权限即将过期时，无法主动请求续期。目前唯一与"管理"相关的端点是 `updateNotificationPreference`（仅管理通知偏好，不管理权限本身）。
- **所在位置**：§1.3 S3 REST 端点表（:131–140），对比 `requirement.md:23`
- **严重程度**：一般
- **改进建议**：二选一：(a) 补充 `DELETE /api/v1/guardianship/{driverId}/permissions` 家属主动撤销自身监护权限的端点；(b) 在文档中明确说明"管理"指自动化的系统侧权限生命周期管理，家属无需（也不提供）手动管理入口，并阐明设计理由。

### 问题 6：跨文档 `StatusColor` 枚举值不一致 — 应用层引入了领域层未定义的 `ORANGE`

- **问题描述**：领域层 OOD（`docs/ood_domain.md:357`）定义 `StatusColor` 为 GREEN / YELLOW / RED 三值。API OOD §1.1 `GetDriverRiskStatusResponse.derivedStatusColor` 同样使用三值，并添加了"此三值枚举与领域层 VO-15 StatusColor 定义一致；若应用层引用额外值（如 ORANGE），应以领域层定义为准进行统一"的一致性注解（:38–39）。但 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse.derivedStatusColor` 仍标注为 GREEN / YELLOW / ORANGE / RED 四值（:499），构成跨三层的定义矛盾。第 8 轮已发现此问题但根源未解决。
- **所在位置**：API OOD §1.1（:37–39），对比 `docs/ood_domain.md:357`、`docs/ood_application.md:499`
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse` 中将 `ORANGE` 移除，统一为 GREEN / YELLOW / RED，与领域层定义保持一致。

### 问题 7：跨文档 DTO 不一致 — 多处 DTO 字段在应用层 OOD 中缺失

- **问题描述**：以下 DTO 字段在 API OOD 中已定义但在 `docs/ood_application.md` 对应的应用层 DTO 中缺失，分别构成独立的跨层不一致：
  - `AlertSummary.gpsLocation`（API OOD §1.1 :53 / §4.1 :1676）→ 应用层 OOD §4.1 `AlertSummary`（:512–518）缺少此字段。第 5 轮发现。
  - `QueryTrajectoryResponse.dataConsistency`（API OOD §1.4 :392）→ 应用层 OOD §4.4 `QueryTrajectoryResponse`（:766–768）仅在注释中提及（:764），DTO 字段列表中未包含。第 3 轮发现。
- **所在位置**：见上述行号
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` 中分别为 `AlertSummary` 补充 `gpsLocation: Optional<GeoPoint>` 字段，为 `QueryTrajectoryResponse` 补充 `dataConsistency: DataConsistency` 字段及 `DataConsistency` 枚举定义。

### 问题 8：§3.2 豁免触发条件表述不精确

- **问题描述**：§3.2 高危失能场景豁免机制的描述中（:1490），将豁免触发条件表述为"S1 RiskMonitoringService 判定 COLLISION_DISABILITY（碰撞失能）或 LIFE_DETECTION 类型告警持续异常 ≥60s"。但根据领域层 OOD，`LIFE_DETECTION` 告警由 `LifeDetectionService`（独立的事件触发型服务，见 `docs/ood_domain.md:548`）在熄火落锁后的 60 秒判定窗口内判定成立后一次性产出 `LifeDetectedEvent`，不存在"持续异常 ≥60s"的概念——这是一个判定窗口事件，而非持续异常监测。当前表述容易让下游实现者误以为需要实现一个"持续 ≥60s"的计时判定逻辑，与领域层的判定模型不符。COLLISION_DISABILITY 的描述也存在类似问题：它由 `EmergencyActivatedEvent` 即时触发，不是持续状态。
- **所在位置**：§3.2（:1490）
- **严重程度**：一般
- **改进建议**：将豁免触发条件修改为引用具体领域事件而非模糊的时间条件，例如："S1 RiskMonitoringService 所在边缘侧产出 `LifeDetectedEvent`（判定窗口内检测到遗留生命 → AlertType=LIFE_DETECTION）或 `EmergencyActivatedEvent`（碰撞失能 → AlertType=COLLISION_DISABILITY）后，经 MQTT 上报至云端，S3 据此激活豁免"。同时移除"持续异常 ≥60s"的计时描述，明确这是 LifeDetectionService 判定的触发条件而非持续监测条件。

---

## 二、整体质量评价

产出覆盖了需求要求的全部五个设计部分（REST API 契约、MQTT 主题设计、WebSocket/SparkRTC 集成、ArkTS 前端对接契约、安全设计），六项应用服务的端点清单完整，MQTT Payload Schema 在经历多轮迭代后覆盖率显著提升。v10 成功落地了持续 6 轮未解决的 `createRescueReport` 接口契约问题，Markdown 格式错误和版本号混乱等问题也已修正。

但仍存在以下结构性不足：(1) 认证链路缺少起点——无登录端点导致 JWT 整个认证机制无法启动；(2) MQTT Payload 的 GPS 字段命名在 v10 修订中仅部分统一，文档内部仍存在不一致；(3) 多轮迭代遗留的跨文档 DTO 不一致问题（涉及 `secondaryAuthToken`、StatusColor、AlertSummary.gpsLocation 等）持续标记为"保留（无需修改本文件）"，但根源在于 `docs/ood_application.md` 未同步更新，下游实现者若以应用层 OOD 为开发依据将遇到字段缺失；(4) §3.2 豁免机制的触发条件表述与领域层判定模型存在偏差。

综合来看，产出在自我完备性（作为独立契约文档）上质量良好，但在跨文档一致性上存在多轮未解决的结构性问题。建议在解决上述 8 项问题（尤其是问题 1 和 2）后，产出可达到直接投入使用标准。

---

## 三、审查依据

审查过程中查阅了以下参考文件以验证事实性判断：
- 需求文档：`/home/jasper/AIoT/docs/requirements.md`
- 领域层 OOD：`/home/jasper/AIoT/docs/ood_domain.md`（§1 RiskLevel/AlertType/StatusColor 枚举定义，§3.8 LifeDetectionService 职责描述）
- 应用层 OOD：`/home/jasper/AIoT/docs/ood_application.md`（§3 各接口方法契约表，§4 各 DTO 定义）
- 历史迭代反馈：`/home/jasper/AIoT/redeliberations/202606291304_api_ood/iteration_history.md`
