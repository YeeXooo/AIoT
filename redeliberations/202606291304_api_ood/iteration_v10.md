# 再审议判定报告（v10）

## 判定结果

RETRY

## 判定理由

组件B诊断报告识别出8项问题，含1项严重问题（问题1：缺少JWT登录端点导致认证链路不完整）、4项一般问题（问题2–4：MQTT GPS字段不一致、跨文档DTO缺失`sparkRTCRoomId`/`sparkRTCJoinToken`、跨文档DTO缺失`secondaryAuthToken`）及3项轻微问题（问题5–8：权限管理端点缺失、StatusColor枚举不一致、多处DTO字段缺失、豁免触发条件表述不精确）。质询报告结果为LOCATED，确认所有问题举证准确、逻辑自洽、覆盖完备。实际轮次1 < 最大轮次12，质询提前终止且审查结论被确认。

因诊断报告包含严重和一般等级的问题，根据判定标准须返回RETRY。

## 需要解决的问题

- **问题描述**：缺少JWT登录/Token签发端点，认证链路不完整
- **所在位置**：§1 REST API 契约（缺失），§5.1（:1826–1832 描述了签发流程但缺少对应端点定义）
- **严重程度**：严重
- **改进建议**：在 §1.7 或新增 §1.8 中补充 `POST /api/v1/auth/login` 端点定义（含 `LoginRequest` / `LoginResponse`，支持用户名/密码和手机验证码两种登录方式），并明确其跳过 JWT 校验（为匿名端点）。

- **问题描述**：MQTT AlertTriggeredEvent（family push）GPS 字段命名未随 SafetyAlertEvent 同步更新，文档内部不一致
- **所在位置**：§2.2 AlertTriggeredEvent 表（:1288）、TripStatus 事件（:1126）、OverrideSignal（:1207），对比 SafetyAlertEvent（:830）和 DriverStatusSnapshot（:862）
- **严重程度**：一般
- **改进建议**：二选一：(a) 将文档内所有 MQTT Payload 的 GPS 字段统一为 `gpsLocation`；(b) 在 §2.2 开头新增字段命名约定说明块，明确解释各 Topic 的 GPS 字段命名规则及其设计理由。

- **问题描述**：跨文档 DTO 不一致 — `RequestMediaSessionResponse` 应用层 OOD 缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`
- **所在位置**：API OOD §1.3（:167–175）、§4.1（:1563–1568），对比 `docs/ood_application.md` §4.3（:584–586）
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段。

- **问题描述**：跨文档 DTO 不一致 — `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 缺少 `secondaryAuthToken`
- **所在位置**：API OOD §1.3（:161, :200），对比 `docs/ood_application.md` §4.3（:577–580, :596–598）
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

- **问题描述**：S3 家属权限管理只有查询端点，缺少主动管理入口
- **所在位置**：§1.3 S3 REST 端点表（:131–140），对比 `requirement.md:23`
- **严重程度**：轻微
- **改进建议**：二选一：(a) 补充 `DELETE /api/v1/guardianship/{driverId}/permissions`；(b) 在文档中明确说明设计理由。

- **问题描述**：跨文档 `StatusColor` 枚举值不一致 — 应用层引入了领域层未定义的 `ORANGE`
- **所在位置**：API OOD §1.1（:37–39），对比 `docs/ood_domain.md:357`、`docs/ood_application.md:499`
- **严重程度**：轻微
- **改进建议**：在 `docs/ood_application.md` §4.1 中将 `ORANGE` 移除，统一为 GREEN / YELLOW / RED。

- **问题描述**：跨文档 DTO 不一致 — 多处 DTO 字段在应用层 OOD 中缺失（`AlertSummary.gpsLocation`、`QueryTrajectoryResponse.dataConsistency`）
- **所在位置**：见诊断报告问题7行号
- **严重程度**：轻微
- **改进建议**：在 `docs/ood_application.md` 中补充对应字段。

- **问题描述**：§3.2 豁免触发条件表述不精确
- **所在位置**：§3.2（:1490）
- **严重程度**：轻微
- **改进建议**：将豁免触发条件修改为引用具体领域事件，移除"持续异常 ≥60s"的计时描述。
