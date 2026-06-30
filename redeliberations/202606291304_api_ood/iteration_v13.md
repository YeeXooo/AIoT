# 再审议判定报告（v13）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（v2）经质询确认为 LOCATED，审查结论成立。诊断报告识别出以下问题：

1. **严重（问题7）**：跨文档 DTO 不一致 —— 应用层 `RequestMediaSessionResponse` 缺少 `sparkRTCRoomId`、`sparkRTCJoinToken`，导致 REST 对讲降级路径无法交付 SparkRTC 入房凭证，违反 `requirement.md:48` 约束。
2. **中等（问题1）**：§3.1 WebSocket `access_granted` 下行消息 Payload 缺少 `reason` 字段，影响 BR-06 紧急救援路径下家属端自动接入音视频功能。
3. **中等（问题8）**：跨文档 DTO 不一致 —— 应用层 `RequestMediaSessionRequest`、`TriggerManualRescueRequest` 缺少 `secondaryAuthToken`，导致 §5.2 二次身份验证门控无法落地。
4. **一般（问题2）**：§3.1 `access_revoked` Payload 枚举值不完整。
5. **一般（问题3）**：§1.6 `QueryUpgradeHistoryResponse.finalStatus` 枚举值未在 API OOD 中定义。
6. **轻微（问题4）**：§1.6 S6 `400` 错误码覆盖场景不完整。
7. **轻微（问题5）**：SparkRTC token 端点独立调用场景未阐明。
8. **轻微（问题6）**：§2.1 `app/{accountId}/rescue/confirm` 消费者归属不明确。
9. **轻微（问题9）**：跨文档 `StatusColor` 枚举在应用层仍含 `ORANGE`。

依据判定标准：诊断报告包含严重（1项）和一般/中等（4项）等级问题，不满足 PASS 条件，应判定为 RETRY。

组件B内部循环实际轮次 2 < 最大轮次 12，质询报告结论为 LOCATED，确认审查发现的问题真实存在。

## 需要解决的问题

- **问题描述**：跨文档不一致 —— 应用层 `RequestMediaSessionResponse` 缺少 `sparkRTCRoomId`、`sparkRTCJoinToken` 字段，导致 REST 对讲降级通道无法交付 SparkRTC 入房凭证，违反 `requirement.md:48` 约束
- **所在位置**：API OOD `a_v13_copy_from_v12.md:184–196`、`:1669–1674`、`:1606`；应用层 OOD `docs/ood_application.md:584–586`
- **严重程度**：严重
- **改进建议**：在 `docs/ood_application.md:584–586` 的 `RequestMediaSessionResponse` 中补 `sparkRTCRoomId: String` 与 `sparkRTCJoinToken: String`；补齐后在本产出 §1.3 / §4.1 标注跨层 DTO 已对齐

- **问题描述**：§3.1 WebSocket `access_granted` 下行消息 Payload 缺少 `reason` 字段，与 §2.2 MQTT 事件及 §4.1 ArkTS 接口不一致，导致高危失能场景下家属端无法自动接入音视频
- **所在位置**：API OOD `a_v13_copy_from_v12.md:1494`（§3.1 下行消息表 `access_granted` 行）
- **严重程度**：中等
- **改进建议**：在 §3.1 `access_granted` 行 Payload 补 `"reason": "REGULAR_60S | EMERGENCY_ACTIVATION | OCCLUSION_RECOVERY"`，与 MQTT 事件、ArkTS 接口对齐

- **问题描述**：跨文档不一致 —— 应用层 `RequestMediaSessionRequest`、`TriggerManualRescueRequest` 缺少 `secondaryAuthToken` 字段，导致 §5.2 二次身份验证门控无法落地
- **所在位置**：API OOD `a_v13_copy_from_v12.md:177`、`:216`；应用层 OOD `docs/ood_application.md:577–580`、`:596–598`
- **严重程度**：中等
- **改进建议**：在 `docs/ood_application.md` 的 `RequestMediaSessionRequest` 与 `TriggerManualRescueRequest` 中补 `secondaryAuthToken: String`

- **问题描述**：§3.1 `access_revoked` Payload 枚举值仅示单值 `RISK_DECLINED`，与 §2.2/§4.1 的三值定义不一致
- **所在位置**：API OOD `a_v13_copy_from_v12.md:1495`
- **严重程度**：一般
- **改进建议**：将该行 `reason` 值改为 `"RISK_DECLINED | CAMERA_OCCLUDED | DRIVER_DEACTIVATED"`

- **问题描述**：§1.6 `QueryUpgradeHistoryResponse.finalStatus` 枚举值未在 API OOD 中完整定义
- **所在位置**：API OOD `a_v13_copy_from_v12.md:702`
- **严重程度**：一般
- **改进建议**：补 `finalStatus` 取值说明 `SUCCEEDED | FAILED | ROLLED_BACK`
