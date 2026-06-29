# 再审议判定报告（v4）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v4_diag_v2）经质询报告（b_v4_challenge_v2）确认（质询结果：LOCATED；实际轮次 2 < 最大轮次 12，提前终止），两报告结论一致，诊断结论可信。

诊断报告共识别 8 项问题，其中：
- 严重 1 项：S3→S5 手动救援流转引用了未在 IEmergencyRescueService 接口中形式化定义的 `createRescueReport` 方法，阻断实现落地
- 一般 3 项：跨层 DTO 一致性缺失（问题 2、3）、高危失能场景 SparkRTC 会话时长豁免机制未定义（问题 4）
- 轻微 4 项：安全配置内部矛盾、语义模糊、格式不一致、逻辑张力

质询报告额外指出一项一般问题：诊断报告未显式核查第 1-3 轮历史问题的修复状况，但此不影响对组件A产出的判定。

依据判定标准：审查报告包含严重及一般等级问题，判定为 RETRY。

## 需要解决的问题

- **问题描述**：S3→S5 手动救援流转引用了 `S5.createRescueReport()` 方法，但 `IEmergencyRescueService` 接口方法表中不存在该方法签名——API OOD 与应用层 OOD 两文档均有共识性引用，接口层缺失形式化契约
- **所在位置**：`a_v4_output_v2.md` §1.3 TriggerManualRescueResponse（行 207-211），关联 `docs/ood_application.md` §3.5 IEmergencyRescueService 接口方法表及 §4.3 TriggerManualRescueResponse DTO
- **严重程度**：严重
- **改进建议**：在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入/输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 Request/Response DTO 定义

- **问题描述**：API OOD 的 `RequestMediaSessionRequest` 包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段，导致前端传入的二次验证凭证在反序列化时丢失
- **所在位置**：`a_v4_output_v2.md` §1.3（行 149-156）vs `docs/ood_application.md` §4.3（行 576-579）
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.3 `RequestMediaSessionRequest` DTO 中补充 `secondaryAuthToken: String` 字段

- **问题描述**：API OOD 的 `TriggerManualRescueRequest` 包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段，与问题 2 性质相同
- **所在位置**：`a_v4_output_v2.md` §1.3（行 189-195）vs `docs/ood_application.md` §4.3（行 595-598）
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.3 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段

- **问题描述**：高危失能场景下 SparkRTC 会话时长豁免机制未定义——未说明豁免触发条件、Token 10 分钟硬编码有效期如何与豁免后延长会话协调、是否需要重新签发 Token
- **所在位置**：`a_v4_output_v2.md` §3.2 SparkRTC 房间参数表（行 1378）及 Token 签发（行 1404）
- **严重程度**：一般
- **改进建议**：补充豁免机制的触发条件、Token 续期策略，或明确标注此场景下的 Token 处理方式为待定项
